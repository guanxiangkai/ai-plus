package io.github.guanxiangkai.artifact.plus.maven;

import io.github.guanxiangkai.artifact.plus.ArtifactProtector;
import io.github.guanxiangkai.artifact.plus.ProtectionRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/**
 * 在 Maven {@code verify} 阶段生成可选保护制品，并保留原始 JAR 作为主制品。
 */
public final class ProtectMojo extends AbstractMojo {

    private boolean enabled;
    private boolean obfuscationEnabled;
    private boolean signingEnabled;
    private List<String> packages = List.of();
    private File keepRules;
    private File inputJar;
    private File outputJar;
    private File mappingFile;
    private File privateKeyFile;
    private File publicKeyFile;
    private List<Artifact> pluginArtifacts = List.of();
    private MavenProject project;
    private MavenProjectHelper projectHelper;

    /**
     * 调用核心保护器；关闭时由核心清理本次保护输出，成功时以 classifier 附加制品。
     *
     * @throws MojoExecutionException 保护失败时终止 Maven 构建
     */
    @Override
    public void execute() throws MojoExecutionException {
        try {
            new ArtifactProtector().protect(new ProtectionRequest(
                    requiredPath(inputJar, "inputJar"),
                    requiredPath(outputJar, "outputJar"),
                    requiredPath(mappingFile, "mappingFile"),
                    enabled,
                    obfuscationEnabled,
                    signingEnabled,
                    packages == null ? List.of() : List.copyOf(packages),
                    optionalPath(keepRules),
                    projectLibraries(),
                    optionalPath(privateKeyFile),
                    optionalPath(publicKeyFile),
                    obfuscatorClasspath()
            ));
            if (enabled) {
                projectHelper.attachArtifact(project, "jar", "protected", outputJar);
                if (signingEnabled) {
                    projectHelper.attachArtifact(project, "jar.sig", "protected", signaturePath(outputJar).toFile());
                }
            }
        } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
            throw new MojoExecutionException("业务制品保护失败，请检查 protect 配置、输入制品和密钥文件路径", exception);
        }
    }

    private List<Path> projectLibraries() {
        if (project.getArtifacts() == null) {
            return List.of();
        }
        return project.getArtifacts().stream()
                .map(Artifact::getFile)
                .filter(java.util.Objects::nonNull)
                .map(File::toPath)
                .toList();
    }

    private List<Path> obfuscatorClasspath() {
        if (pluginArtifacts == null) {
            return List.of();
        }
        return pluginArtifacts.stream()
                .map(Artifact::getFile)
                .filter(java.util.Objects::nonNull)
                .map(File::toPath)
                .toList();
    }

    private static Path requiredPath(File file, String parameterName) {
        if (file == null) {
            throw new IllegalArgumentException(parameterName + " 不能为空");
        }
        return file.toPath();
    }

    private static Path optionalPath(File file) {
        return file == null ? null : file.toPath();
    }

    private static Path signaturePath(File output) {
        Path outputPath = output.toPath();
        return outputPath.resolveSibling(outputPath.getFileName() + ".sig");
    }
}
