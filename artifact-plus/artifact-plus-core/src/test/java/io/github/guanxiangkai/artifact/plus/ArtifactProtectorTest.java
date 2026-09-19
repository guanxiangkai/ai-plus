package io.github.guanxiangkai.artifact.plus;

import io.github.guanxiangkai.artifact.plus.signing.ArtifactSignatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 以真实 JAR 与外部 ProGuard 进程验证制品保护边界。 */
class ArtifactProtectorTest {

    @TempDir
    Path directory;

    private ArtifactProtector protector;
    private Path input;
    private Path output;
    private Path mapping;
    private Path privateKey;
    private Path publicKey;

    @BeforeEach
    void setUp() throws Exception {
        protector = new ArtifactProtector();
        input = compileBusinessJar("input.jar", false);
        output = directory.resolve("protected.jar");
        mapping = directory.resolve("mapping.txt");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair keys = generator.generateKeyPair();
        privateKey = directory.resolve("private.der");
        publicKey = directory.resolve("public.der");
        Files.write(privateKey, keys.getPrivate().getEncoded());
        Files.write(publicKey, keys.getPublic().getEncoded());
    }

    @Test
    void disabledProtectionCleansOutputsWithoutChangingInput() throws Exception {
        byte[] original = Files.readAllBytes(input);
        Files.writeString(output, "old output");
        Files.writeString(signature(), "old signature");
        Files.writeString(mapping, "old mapping");

        protector.protect(request(false, false, false, List.of(), null, toolClasspath()));

        assertArrayEquals(original, Files.readAllBytes(input));
        assertFalse(Files.exists(output));
        assertFalse(Files.exists(signature()));
        assertFalse(Files.exists(mapping));
    }

    @Test
    void rejectsInputKeyAndFilesystemAliasesAsOutputsWithoutChangingInput() throws Exception {
        byte[] original = Files.readAllBytes(input);
        assertThrows(IOException.class, () -> protector.protect(request(input, mapping, false, false)));
        assertThrows(IOException.class, () -> protector.protect(request(output, input, false, false)));
        assertThrows(IOException.class, () -> protector.protect(request(privateKey, mapping, true, true)));
        assertThrows(IOException.class, () -> protector.protect(request(output, publicKey, true, true)));

        Path hardLink = directory.resolve("input-hard-link.jar");
        Files.createLink(hardLink, input);
        assertThrows(IOException.class, () -> protector.protect(request(hardLink, mapping, false, false)));

        Path symbolicLink = directory.resolve("input-symbolic-link.jar");
        Files.createSymbolicLink(symbolicLink, input.getFileName());
        assertThrows(IOException.class, () -> protector.protect(request(symbolicLink, mapping, false, false)));
        assertArrayEquals(original, Files.readAllBytes(input));
    }

    @Test
    void obfuscatesOnlySelectedBusinessMembersAndPreservesRuntimeAnnotations() throws Exception {
        protector.protect(request(true, false, List.of("demo.business", "demo.dto"), null, toolClasspath()));

        assertTrue(Files.isRegularFile(output));
        assertTrue(Files.isRegularFile(mapping));
        try (URLClassLoader loader = new URLClassLoader(new URL[] {output.toUri().toURL()})) {
            Class<?> calculator = loader.loadClass("demo.business.Calculator");
            assertEquals(42, calculator.getMethod("evaluate", int.class).invoke(null, 21));
            Class<?> utility = loader.loadClass("other.Utility");
            var privateUtility = utility.getDeclaredMethod("privateUtility");
            privateUtility.setAccessible(true);
            assertEquals(5, privateUtility.invoke(null));
            Class<?> invoice = loader.loadClass("demo.dto.Invoice");
            assertTrue(hasRuntimeAnnotation(invoice, "demo.annotation.RuntimeTag"));
        }
        String result = Files.readString(mapping);
        assertTrue(result.contains("secret(int) ->"));
        assertFalse(result.contains("secret(int) -> secret"));
        assertTrue(result.contains("privateUtility() -> privateUtility"));
        try (ZipFile archive = new ZipFile(output.toFile())) {
            assertTrue(Objects.isNull(archive.getEntry("mapping.txt")));
        }
    }

    @Test
    void signsExactInputBytesAndSignsObfuscatedOutput() throws Exception {
        byte[] original = Files.readAllBytes(input);
        protector.protect(request(true, true, false, List.of(), null, List.of()));
        assertArrayEquals(original, Files.readAllBytes(output));
        assertDoesNotThrow(() -> ArtifactSignatures.verify(output, signature(), publicKey));

        protector.protect(request(true, true, List.of("demo.business"), null, toolClasspath()));
        assertDoesNotThrow(() -> ArtifactSignatures.verify(output, signature(), publicKey));
        assertTrue(Files.isRegularFile(mapping));
    }

    @Test
    void rejectsInvalidObfuscationRequestsAndRemovesOldOutputs() throws Exception {
        assertRejectedAndCleaned(request(true, false, List.of(), null, toolClasspath()));
        assertRejectedAndCleaned(request(true, false, List.of("missing.package"), null, toolClasspath()));
        assertRejectedAndCleaned(request(true, false, List.of("demo.business"), null,
                List.of(directory.resolve("missing-proguard.jar"))));
    }

    @Test
    void appliesValidKeepRulesAndRejectsUnsafeRules() throws Exception {
        Path keepRules = directory.resolve("keep.pro");
        Files.writeString(keepRules, "-keepclassmembers class demo.business.Calculator { private static int secret(int); }\n");
        protector.protect(request(true, false, List.of("demo.business"), keepRules, toolClasspath()));
        assertTrue(Files.readString(mapping).contains("secret(int) -> secret"));

        Files.writeString(keepRules, "-dontobfuscate\n");
        assertRejectedAndCleaned(request(true, false, List.of("demo.business"), keepRules, toolClasspath()));
        Files.writeString(keepRules, "-outjars replacement.jar\n");
        assertRejectedAndCleaned(request(true, false, List.of("demo.business"), keepRules, toolClasspath()));
        Files.writeString(keepRules, "-keep class demo.dto.** { *; } @extra.pro\n");
        assertRejectedAndCleaned(request(true, false, List.of("demo.business"), keepRules, toolClasspath()));
    }

    @Test
    void readsBusinessClassesAndResourcesFromSameSnapshot() throws Exception {
        Path work = Files.createDirectory(directory.resolve("snapshot-work"));
        Path snapshot = Files.copy(input, work.resolve("input.jar"));
        // 处理期间原始构建路径被替换，混淆和重打包仍必须读取同一快照。
        Files.writeString(input, "subsequent build replaced original input");
        new JarObfuscator().obfuscate(request(true, false, List.of("demo.business"), null, toolClasspath()),
                snapshot, output, mapping, work);
        try (URLClassLoader loader = new URLClassLoader(new URL[] {output.toUri().toURL()})) {
            assertEquals(42, loader.loadClass("demo.business.Calculator")
                    .getMethod("evaluate", int.class).invoke(null, 21));
        }
        assertTrue(Files.readString(mapping).contains("secret(int) ->"));
    }

    @Test
    void keepsBootNestedDependencyStoredAndUnchanged() throws Exception {
        Path bootInput = compileBusinessJar("boot-input.jar", true);
        Path nested = directory.resolve("dependency.jar");
        writeZip(nested, Map.of("dependency.txt", "dependency".getBytes(StandardCharsets.UTF_8)), false);
        appendStoredNestedJar(bootInput, nested);
        input = bootInput;

        protector.protect(request(true, false, List.of("demo.business"), null, toolClasspath()));

        try (ZipFile source = new ZipFile(input.toFile()); ZipFile protectedJar = new ZipFile(output.toFile())) {
            ZipEntry original = source.getEntry("BOOT-INF/lib/dependency.jar");
            ZipEntry copied = protectedJar.getEntry("BOOT-INF/lib/dependency.jar");
            assertEquals(ZipEntry.STORED, copied.getMethod());
            assertArrayEquals(source.getInputStream(original).readAllBytes(), protectedJar.getInputStream(copied).readAllBytes());
        }
    }

    @Test
    void rejectsMalformedSignedAndMultiReleaseInputsForObfuscationButAllowsSigningOnly() throws Exception {
        byte[] calculator = classBytes("demo/business/Calculator.class");
        Path malformed = directory.resolve("malformed.jar");
        Files.writeString(malformed, "not a jar");
        assertObfuscationRejectedButSigningAllowed(malformed);

        Path signed = directory.resolve("embedded-signature.jar");
        writeZip(signed, Map.of("demo/business/Calculator.class", calculator,
                "META-INF/APP.SF", "signature".getBytes(StandardCharsets.UTF_8)), false);
        assertObfuscationRejectedButSigningAllowed(signed);

        Path multiRelease = directory.resolve("multi-release.jar");
        writeZip(multiRelease, Map.of("demo/business/Calculator.class", calculator,
                "META-INF/versions/25/demo/business/Calculator.class", calculator), false);
        assertObfuscationRejectedButSigningAllowed(multiRelease);
    }

    private void assertObfuscationRejectedButSigningAllowed(Path candidate) throws Exception {
        input = candidate;
        assertRejectedAndCleaned(request(true, false, List.of("demo.business"), null, toolClasspath()));
        protector.protect(request(true, true, false, List.of(), null, List.of()));
        assertArrayEquals(Files.readAllBytes(candidate), Files.readAllBytes(output));
        assertDoesNotThrow(() -> ArtifactSignatures.verify(output, signature(), publicKey));
    }

    private void assertRejectedAndCleaned(ProtectionRequest request) throws Exception {
        Files.writeString(output, "stale");
        Files.writeString(signature(), "stale");
        Files.writeString(mapping, "stale");
        byte[] original = Files.readAllBytes(input);
        assertThrows(IOException.class, () -> protector.protect(request));
        assertArrayEquals(original, Files.readAllBytes(input));
        assertFalse(Files.exists(output));
        assertFalse(Files.exists(signature()));
        assertFalse(Files.exists(mapping));
    }

    private ProtectionRequest request(boolean enabled, boolean sign, boolean obfuscate, List<String> packages,
                                      Path keepRules, List<Path> tools) {
        return new ProtectionRequest(input, output, mapping, enabled, obfuscate, sign, packages, keepRules,
                List.of(), privateKey, publicKey, tools);
    }

    private ProtectionRequest request(boolean obfuscate, boolean sign, List<String> packages,
                                      Path keepRules, List<Path> tools) {
        return request(true, sign, obfuscate, packages, keepRules, tools);
    }

    private ProtectionRequest request(Path targetOutput, Path targetMapping, boolean obfuscate, boolean sign) {
        return new ProtectionRequest(input, targetOutput, targetMapping, true, obfuscate, sign,
                List.of("demo.business"), null, List.of(), privateKey, publicKey, toolClasspath());
    }

    private Path signature() {
        return output.resolveSibling(output.getFileName() + ".sig");
    }

    private List<Path> toolClasspath() {
        String configured = System.getProperty("artifact.test.obfuscatorClasspath");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("artifact.test.obfuscatorClasspath is required for obfuscation tests");
        }
        return List.of(configured.split(java.util.regex.Pattern.quote(File.pathSeparator))).stream()
                .filter(value -> !value.isBlank()).map(Path::of).toList();
    }

    private Path compileBusinessJar(String name, boolean bootLayout) throws Exception {
        Path sources = directory.resolve("sources-" + name);
        writeSource(sources, "demo/annotation/RuntimeTag.java", """
                package demo.annotation;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface RuntimeTag { }
                """);
        writeSource(sources, "demo/business/Calculator.java", """
                package demo.business;
                public class Calculator {
                    public static int evaluate(int value) { return secret(value); }
                    private static int secret(int value) { return value * 2; }
                }
                """);
        writeSource(sources, "demo/dto/Invoice.java", """
                package demo.dto;
                import demo.annotation.RuntimeTag;
                @RuntimeTag public class Invoice { private String value = "invoice"; }
                """);
        writeSource(sources, "other/Utility.java", """
                package other;
                public class Utility {
                    private static int privateUtility() { return 5; }
                    private static int hidden() { return 7; }
                }
                """);
        Path classes = directory.resolve("classes-" + name);
        compile(sources, classes);
        Path jar = directory.resolve(name);
        try (ZipOutputStream stream = new ZipOutputStream(Files.newOutputStream(jar))) {
            try (var paths = Files.walk(classes)) {
                for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                    String entry = classes.relativize(file).toString().replace(File.separatorChar, '/');
                    putEntry(stream, (bootLayout ? "BOOT-INF/classes/" : "") + entry, Files.readAllBytes(file), false);
                }
            }
        }
        return jar;
    }

    private void compile(Path sources, Path classes) throws IOException {
        JavaCompiler compiler = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler(), "JDK compiler is required");
        Files.createDirectories(classes);
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            List<File> sourceFiles;
            try (var paths = Files.walk(sources)) {
                sourceFiles = paths.filter(path -> path.toString().endsWith(".java")).map(Path::toFile).toList();
            }
            boolean compiled = compiler.getTask(null, files, null, List.of("--release", "25", "-d", classes.toString()),
                    null, files.getJavaFileObjectsFromFiles(sourceFiles)).call();
            if (!compiled) throw new IOException("fixture compilation failed");
        }
    }

    private void writeSource(Path sources, String relativePath, String source) throws IOException {
        Path file = sources.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    private void appendStoredNestedJar(Path jar, Path nested) throws IOException {
        Path replacement = directory.resolve("boot-with-nested.jar");
        try (ZipFile existing = new ZipFile(jar.toFile()); ZipOutputStream stream = new ZipOutputStream(Files.newOutputStream(replacement))) {
            var entries = existing.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                putEntry(stream, entry.getName(), existing.getInputStream(entry).readAllBytes(), false);
            }
            putEntry(stream, "BOOT-INF/lib/dependency.jar", Files.readAllBytes(nested), true);
        }
        Files.move(replacement, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeZip(Path jar, Map<String, byte[]> entries, boolean stored) throws IOException {
        try (ZipOutputStream stream = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) putEntry(stream, entry.getKey(), entry.getValue(), stored);
        }
    }

    private void putEntry(ZipOutputStream stream, String name, byte[] content, boolean stored) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        if (stored) {
            CRC32 crc = new CRC32();
            crc.update(content);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            entry.setCrc(crc.getValue());
        }
        stream.putNextEntry(entry);
        stream.write(content);
        stream.closeEntry();
    }

    private byte[] classBytes(String path) throws IOException {
        try (ZipFile archive = new ZipFile(input.toFile())) {
            return archive.getInputStream(Objects.requireNonNull(archive.getEntry(path))).readAllBytes();
        }
    }

    private boolean hasRuntimeAnnotation(Class<?> type, String annotationName) {
        for (Annotation annotation : type.getAnnotations()) {
            if (annotation.annotationType().getName().equals(annotationName)) return true;
        }
        return false;
    }
}
