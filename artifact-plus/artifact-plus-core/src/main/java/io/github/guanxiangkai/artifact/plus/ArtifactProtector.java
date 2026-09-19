package io.github.guanxiangkai.artifact.plus;

import io.github.guanxiangkai.artifact.plus.signing.ArtifactSignatures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 统一编排业务 JAR 混淆和完整制品签名，不改变输入文件。 */
public final class ArtifactProtector {
    /** 创建无共享可变状态的制品处理器。 */
    public ArtifactProtector() {}

    /**
     * 生成保护制品；失败清除保护输出，关闭时清除旧输出。
     * 调用方必须给每个任务分配独占输出路径，不可并发写入同一制品。
     *
     * @param request 保护参数；密钥仅由外部路径提供
     * @throws IOException 路径冲突、无效 JAR 或混淆失败
     * @throws GeneralSecurityException 密钥或签名验证失败
     */
    public void protect(ProtectionRequest request) throws IOException, GeneralSecurityException {
        Path output = request.output().toAbsolutePath().normalize();
        Path mapping = request.mapping().toAbsolutePath().normalize();
        Path signature = output.resolveSibling(output.getFileName() + ".sig");
        List<Path> outputs = List.of(output, mapping, signature);
        validatePaths(request, outputs);
        // 先验证全部路径，不能因错误配置删除输入、规则、依赖或密钥。
        for (Path path : outputs) Files.deleteIfExists(path);
        if (!request.enabled()) return;
        if (!request.obfuscate() && !request.sign()) {
            throw new IOException("保护已开启，但没有启用混淆或签名");
        }
        if (!Files.isRegularFile(request.input())) throw new IOException("输入 JAR 不存在");
        if (request.sign() && (request.privateKey() == null || request.publicKey() == null)) {
            throw new IOException("签名需要外部私钥文件及可信公钥文件");
        }
        Files.createDirectories(output.getParent());
        Path work = Files.createTempDirectory(output.getParent(), ".artifact-protection-");
        try {
            Path staged = work.resolve("protected.jar");
            Path stagedMapping = work.resolve("mapping.txt");
            Path snapshot = work.resolve("input.jar");
            snapshot(request.input(), snapshot);
            if (request.obfuscate()) {
                new JarObfuscator().obfuscate(request, snapshot, staged, stagedMapping, work);
            } else {
                Files.move(snapshot, staged);
            }
            if (request.sign()) {
                ArtifactSignatures.sign(staged, work.resolve("protected.jar.sig"),
                        request.privateKey(), request.publicKey());
            }
            if (request.obfuscate()) {
                Files.createDirectories(mapping.getParent());
                Files.move(stagedMapping, mapping, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(staged, output, StandardCopyOption.ATOMIC_MOVE);
            if (request.sign()) {
                Files.move(work.resolve("protected.jar.sig"), signature, StandardCopyOption.ATOMIC_MOVE);
                ArtifactSignatures.verify(output, signature, request.publicKey());
            }
            deleteWork(work);
        } catch (IOException | GeneralSecurityException | RuntimeException | Error failure) {
            for (Path path : outputs) {
                try { Files.deleteIfExists(path); }
                catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            try { deleteWork(work); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void snapshot(Path input, Path snapshot) throws IOException {
        BasicFileAttributes before = Files.readAttributes(input, BasicFileAttributes.class);
        if (!before.isRegularFile()) throw new IOException("输入 JAR 必须是普通文件");
        Files.copy(input, snapshot);
        BasicFileAttributes after = Files.readAttributes(input, BasicFileAttributes.class);
        if (!after.isRegularFile() || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey()) || Files.size(snapshot) != before.size()) {
            throw new IOException("输入 JAR 在创建快照期间发生变化");
        }
    }

    private static void deleteWork(Path work) throws IOException {
        if (!Files.exists(work)) return;
        try (var files = Files.walk(work)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void validatePaths(ProtectionRequest request, List<Path> outputs) throws IOException {
        List<Path> inputs = new ArrayList<>(request.libraries());
        inputs.addAll(request.obfuscatorClasspath());
        inputs.add(request.input());
        if (request.keepRules() != null) inputs.add(request.keepRules());
        if (request.privateKey() != null) inputs.add(request.privateKey());
        if (request.publicKey() != null) inputs.add(request.publicKey());
        for (int index = 0; index < outputs.size(); index++) {
            Path output = outputs.get(index);
            // 符号链接输出容易将清理或替换引向调用方未预期的文件。
            if (Files.isSymbolicLink(output) || Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("保护输出必须是普通文件路径");
            }
            Path resolved = resolve(output);
            for (Path input : inputs) {
                if (same(output, input) || resolved.startsWith(resolve(input))) {
                    throw new IOException("保护输出不能覆盖输入、依赖、规则或密钥");
                }
            }
            for (int other = 0; other < index; other++) {
                Path previous = outputs.get(other);
                if (same(output, previous) || resolved.startsWith(resolve(previous))
                        || resolve(previous).startsWith(resolved)) {
                    throw new IOException("保护输出、签名和映射路径不能重合或互相包含");
                }
            }
        }
    }

    private static boolean same(Path first, Path second) throws IOException {
        return resolve(first).equals(resolve(second))
                || (Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second));
    }

    private static Path resolve(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (Files.exists(absolute)) return absolute.toRealPath();
        Path parent = absolute.getParent();
        return parent == null ? absolute : resolve(parent).resolve(absolute.getFileName());
    }
}
