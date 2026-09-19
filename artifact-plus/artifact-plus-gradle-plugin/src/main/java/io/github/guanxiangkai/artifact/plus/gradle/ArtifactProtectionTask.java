package io.github.guanxiangkai.artifact.plus.gradle;

import io.github.guanxiangkai.artifact.plus.ArtifactProtector;
import io.github.guanxiangkai.artifact.plus.ProtectionRequest;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
/** 执行 JAR 保护并写入独立输出目录的任务。 */
@DisableCachingByDefault(because = "签名和关闭后的清理必须在每次构建中重新执行")
public abstract class ArtifactProtectionTask extends DefaultTask {

    /** 是否执行保护。 */
    @Input
    public abstract Property<Boolean> getProtectionEnabled();

    /** 是否混淆业务类。 */
    @Input
    public abstract Property<Boolean> getObfuscationEnabled();

    /** 是否生成签名。 */
    @Input
    public abstract Property<Boolean> getSigningEnabled();

    /** 需处理的业务包。 */
    @Input
    public abstract ListProperty<String> getPackages();

    /** 输入制品。 */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputJar();

    /** 输出制品。 */
    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    /** 混淆映射。 */
    @OutputFile
    public abstract RegularFileProperty getMappingFile();

    /** 保护制品的伴随签名文件。 */
    @OutputFile
    public abstract RegularFileProperty getSignatureFile();

    /** 可选保留规则。 */
    @InputFile
    @org.gradle.api.tasks.Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getKeepRules();

    /** 用于类解析的依赖库。 */
    @Classpath
    public abstract ConfigurableFileCollection getLibraries();

    /** ProGuard CLI 的独立运行时类路径。 */
    @Classpath
    public abstract ConfigurableFileCollection getObfuscatorClasspath();

    /** 私钥内容不应成为 Gradle 任务输入。 */
    @Internal
    public abstract RegularFileProperty getPrivateKeyFile();

    /** 公钥内容不应成为 Gradle 任务输入。 */
    @Internal
    public abstract RegularFileProperty getPublicKeyFile();

    /** 调用核心保护器，核心负责关闭状态下的输出清理。 */
    @TaskAction
    public void protectArtifact() {
        try {
            new ArtifactProtector().protect(new ProtectionRequest(
                    path(getInputJar()),
                    path(getOutputJar()),
                    path(getMappingFile()),
                    getProtectionEnabled().get(),
                    getObfuscationEnabled().get(),
                    getSigningEnabled().get(),
                    getPackages().get(),
                    optionalPath(getKeepRules()),
                    getLibraries().getFiles().stream().map(java.io.File::toPath).toList(),
                    optionalPath(getPrivateKeyFile()),
                    optionalPath(getPublicKeyFile()),
                    getObfuscatorClasspath().getFiles().stream().map(java.io.File::toPath).toList()
            ));
        } catch (IOException | GeneralSecurityException exception) {
            throw new GradleException("Failed to protect artifact. Check artifactProtection configuration and key file paths.", exception);
        }
    }

    private static Path path(RegularFileProperty property) {
        return property.get().getAsFile().toPath();
    }

    private static Path optionalPath(RegularFileProperty property) {
        return property.isPresent() ? path(property) : null;
    }
}
