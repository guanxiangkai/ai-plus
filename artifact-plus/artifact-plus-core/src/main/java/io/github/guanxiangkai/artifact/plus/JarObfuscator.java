package io.github.guanxiangkai.artifact.plus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** 外部 ProGuard 工具适配；保留资源、类名和 Boot 嵌套 JAR 的存储方式。 */
final class JarObfuscator {
    private static final String BOOT_CLASSES = "BOOT-INF/classes/";
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*");
    void obfuscate(ProtectionRequest request, Path inputJar, Path output, Path mapping, Path work) throws IOException {
        List<String> packages = packages(request.packages());
        if (request.obfuscatorClasspath().isEmpty()) throw new IOException("缺少 ProGuard 工具类路径");
        List<Path> libraries = new ArrayList<>();
        Path program = work.resolve("classes.jar");
        Path transformed = work.resolve("classes-obfuscated.jar");
        String prefix;
        Set<String> names = new HashSet<>();
        int selected = 0;
        try (ZipFile source = new ZipFile(inputJar.toFile());
                ZipOutputStream classes = new ZipOutputStream(Files.newOutputStream(program))) {
            boolean boot = source.stream().anyMatch(entry -> entry.getName().startsWith(BOOT_CLASSES));
            prefix = boot ? BOOT_CLASSES : "";
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!names.add(name) || name.startsWith("/") || name.contains("\\")
                        || List.of(name.split("/")).contains("..")) {
                    throw new IOException("JAR 包含重复或不安全的条目");
                }
                if (isSignature(name)) throw new IOException("混淆输入不能含已有 JAR 内嵌签名");
                if (entry.isDirectory()) continue;
                if (name.startsWith(prefix + "META-INF/versions/") || name.equals(prefix + "module-info.class")) {
                    throw new IOException("混淆暂不支持业务多版本 JAR 或 module-info.class；可单独启用签名");
                }
                if (boot && name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar")) {
                    Path library = work.resolve("dependency-" + libraries.size() + ".jar");
                    try (var input = source.getInputStream(entry)) { Files.copy(input, library); }
                    libraries.add(library);
                }
                if (name.startsWith(prefix) && name.endsWith(".class")) {
                    String relative = name.substring(prefix.length());
                    if (packages.stream().anyMatch(p -> relative.startsWith(p.replace('.', '/') + "/"))) selected++;
                    classes.putNextEntry(new ZipEntry(relative));
                    try (var input = source.getInputStream(entry)) { input.transferTo(classes); }
                    classes.closeEntry();
                }
            }
        }
        if (selected == 0) throw new IOException("指定业务包未匹配任何 class，拒绝生成未混淆制品");
        // Boot JAR 内嵌依赖是最终制品的解析来源；普通 JAR 使用构建工具给出的类路径。
        if (prefix.isEmpty()) libraries.addAll(request.libraries());
        Path configuration = work.resolve("proguard.pro");
        Files.writeString(configuration, configuration(request, packages, program, transformed, mapping, libraries));
        runTool(request.obfuscatorClasspath(), configuration);
        if (!Files.isRegularFile(transformed) || !Files.isRegularFile(mapping)) {
            throw new IOException("混淆器没有生成必需的制品和映射表");
        }
        repackage(inputJar, transformed, output, prefix);
    }

    private static List<String> packages(List<String> values) throws IOException {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = value.endsWith(".**") ? value.substring(0, value.length() - 3) : value;
            if (!PACKAGE.matcher(normalized).matches() || normalized.startsWith("java.")
                    || normalized.equals("java") || normalized.startsWith("org.springframework")) {
                throw new IOException("必须指定合法且明确的业务包前缀");
            }
            result.add(normalized);
        }
        if (result.isEmpty()) throw new IOException("混淆必须显式指定业务包");
        return result;
    }

    private static String configuration(ProtectionRequest request, List<String> packages, Path program,
            Path transformed, Path mapping, List<Path> libraries) throws IOException {
        StringBuilder config = new StringBuilder();
        config.append("-injars ").append(quote(program)).append('\n');
        config.append("-outjars ").append(quote(transformed)).append('\n');
        config.append("-printmapping ").append(quote(mapping)).append('\n');
        for (Path library : libraries) {
            if (!Files.exists(library)) throw new IOException("业务依赖类路径不存在");
            config.append("-libraryjars ").append(quote(library)).append("(!META-INF/versions/**,!module-info.class)\n");
        }
        Path jmods = Path.of(System.getProperty("java.home"), "jmods");
        if (!Files.isDirectory(jmods)) throw new IOException("混淆需要完整 JDK 的 jmods 目录");
        try (var files = Files.list(jmods)) {
            for (Path module : files.filter(p -> p.toString().endsWith(".jmod")).sorted().toList()) {
                config.append("-libraryjars ").append(quote(module)).append("(!**.jar;!module-info.class)\n");
            }
        }
        config.append("""
                -dontshrink
                -dontoptimize
                -forceprocessing
                -keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod,MethodParameters,Record,PermittedSubclasses,NestHost,NestMembers
                -keepparameternames
                -keepnames class **
                -keep interface ** { *; }
                -keep enum ** { *; }
                -keep @** class ** { *; }
                -keep class ** implements java.io.Serializable { *; }
                -keep class ** extends java.lang.Record { *; }
                -keepclassmembers class ** { public *; protected *; @** *; }
                """);
        // 名称白名单只放开用户选择的业务包；框架、第三方类保持成员名。
        config.append("-keep class ");
        for (String name : packages) config.append('!').append(name).append(".**,");
        config.append("** { *; }\n");
        if (request.keepRules() != null) {
            config.append('\n').append(KeepRules.read(request.keepRules())).append('\n');
        }
        return config.toString();
    }

    private static String quote(Path path) throws IOException {
        String value = path.toAbsolutePath().toString();
        if (value.contains("'") || value.contains("\n") || value.contains("\r") || value.contains("<") || value.contains(">")) {
            throw new IOException("工具路径包含不支持的配置字符");
        }
        return "'" + value + "'";
    }

    private static void runTool(List<Path> classpath, Path configuration) throws IOException {
        for (Path path : classpath) if (!Files.isRegularFile(path)) throw new IOException("混淆器工具 JAR 不存在");
        String paths = String.join(File.pathSeparator, classpath.stream().map(p -> p.toAbsolutePath().toString()).toList());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ToolProcess.run(List.of(java, "-cp", paths, "proguard.ProGuard", "@" + configuration),
                Duration.ofMinutes(10));
    }

    private static void repackage(Path input, Path transformed, Path output, String prefix) throws IOException {
        try (ZipFile original = new ZipFile(input.toFile());
                ZipFile classes = new ZipFile(transformed.toFile());
                ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(output))) {
            var entries = original.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                boolean replace = name.startsWith(prefix) && name.endsWith(".class");
                ZipEntry changed = replace ? classes.getEntry(name.substring(prefix.length())) : null;
                if (replace && changed == null) throw new IOException("混淆器改变了类名或移除了业务类");
                ZipEntry destination = new ZipEntry(name);
                destination.setTime(entry.getTime());
                destination.setMethod(entry.getMethod());
                // Boot 对嵌套 JAR 要求 STORED；复制其原始大小和 CRC。
                ZipEntry content = replace ? changed : entry;
                if (destination.getMethod() == ZipEntry.STORED) {
                    destination.setSize(content.getSize());
                    destination.setCompressedSize(content.getSize());
                    destination.setCrc(content.getCrc());
                }
                target.putNextEntry(destination);
                if (!entry.isDirectory()) {
                    try (var source = replace ? classes.getInputStream(changed) : original.getInputStream(entry)) {
                        source.transferTo(target);
                    }
                }
                target.closeEntry();
            }
        }
    }

    private static boolean isSignature(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.startsWith("META-INF/") && upper.indexOf('/', 9) < 0
                && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA")
                || upper.endsWith(".EC") || upper.startsWith("META-INF/SIG-"));
    }
}
