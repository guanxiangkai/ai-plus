package io.github.guanxiangkai.artifact.plus.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * 制品保护插件的构建配置。
 *
 * <p>输入制品使用约定值，显式设置 {@link #getInputJar()} 后不会被 Spring Boot 插件覆盖。</p>
 */
public abstract class ArtifactProtectionExtension {

    /** 是否执行保护；关闭时清理此前生成的保护制品。 */
    public abstract Property<Boolean> getEnabled();

    /** 是否混淆业务类。 */
    public abstract Property<Boolean> getObfuscationEnabled();

    /** 是否为保护制品生成签名。 */
    public abstract Property<Boolean> getSigningEnabled();

    /** 需要保护的业务包。 */
    public abstract ListProperty<String> getPackages();

    /** 待保护的输入 JAR。 */
    public abstract RegularFileProperty getInputJar();

    /** 保护后的输出 JAR。 */
    public abstract RegularFileProperty getOutputJar();

    /** 混淆映射文件。 */
    public abstract RegularFileProperty getMappingFile();

    /** 可选的 ProGuard 保留规则。 */
    public abstract RegularFileProperty getKeepRules();

    /** 解析业务类引用所需的第三方库。 */
    public abstract ConfigurableFileCollection getLibraries();

    /** 独立 ProGuard CLI 进程的运行时类路径，仅启用混淆时解析。 */
    public abstract ConfigurableFileCollection getObfuscatorClasspath();

    /** 可选的签名私钥文件。 */
    public abstract RegularFileProperty getPrivateKeyFile();

    /** 可选的签名公钥文件。 */
    public abstract RegularFileProperty getPublicKeyFile();
}
