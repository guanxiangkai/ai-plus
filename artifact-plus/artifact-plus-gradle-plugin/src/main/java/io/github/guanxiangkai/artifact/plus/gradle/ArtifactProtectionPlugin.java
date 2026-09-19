package io.github.guanxiangkai.artifact.plus.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.util.List;
import java.util.Properties;

/** 将 Java 或 Spring Boot 业务制品交给 artifact-plus-core 保护。 */
public final class ArtifactProtectionPlugin implements Plugin<Project> {

    private static final Attribute<Boolean> PROTECTED_ARTIFACT_ATTRIBUTE = Attribute.of(
            "io.github.guanxiangkai.artifact.protected", Boolean.class);

    /** 插件入口，要求目标工程已应用 Java 插件。 */
    @Override
    public void apply(Project project) {
        ArtifactProtectionExtension extension = project.getExtensions().create(
                "artifactProtection", ArtifactProtectionExtension.class);
        extension.getEnabled().convention(false);
        extension.getObfuscationEnabled().convention(false);
        extension.getSigningEnabled().convention(false);
        extension.getPackages().convention(java.util.List.of());
        extension.getOutputJar().convention(project.getLayout().getBuildDirectory().file(
                "protected/" + project.getName() + ".jar"));
        extension.getMappingFile().convention(project.getLayout().getBuildDirectory().file(
                "protected/mapping.txt"));
        extension.getPrivateKeyFile().convention(project.getLayout().file(project.getProviders()
                .environmentVariable("ARTIFACT_SIGNING_PRIVATE_KEY_FILE").map(File::new)));
        extension.getPublicKeyFile().convention(project.getLayout().file(project.getProviders()
                .environmentVariable("ARTIFACT_SIGNING_PUBLIC_KEY_FILE").map(File::new)));
        Configuration obfuscator = createObfuscatorConfiguration(project.getConfigurations(), project);
        extension.getObfuscatorClasspath().from(project.getProviders().provider(() ->
                extension.getEnabled().getOrElse(false) && extension.getObfuscationEnabled().getOrElse(false)
                        ? obfuscator : List.of()));

        project.getPluginManager().withPlugin("java", ignored -> configureJavaProject(project, extension));
    }

    private Configuration createObfuscatorConfiguration(ConfigurationContainer configurations, Project project) {
        Configuration obfuscator = configurations.maybeCreate("artifactObfuscator");
        obfuscator.setCanBeResolved(true);
        obfuscator.setCanBeConsumed(false);
        obfuscator.defaultDependencies(dependencies -> dependencies.add(project.getDependencies().create(
                "com.guardsquare:proguard-base:" + proguardVersion())));
        return obfuscator;
    }

    private String proguardVersion() {
        Properties properties = new Properties();
        try (InputStream input = ArtifactProtectionPlugin.class.getClassLoader()
                .getResourceAsStream("META-INF/artifact-plus-tool.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing META-INF/artifact-plus-tool.properties.");
            }
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read artifact protection tool metadata.", exception);
        }
        String version = properties.getProperty("proguard.version");
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("Missing proguard.version in artifact protection tool metadata.");
        }
        return version;
    }

    private void configureJavaProject(Project project, ArtifactProtectionExtension extension) {
        Provider<org.gradle.api.file.RegularFile> jarFile = project.getTasks().named("jar", Jar.class)
                .flatMap(Jar::getArchiveFile);
        extension.getInputJar().convention(jarFile);
        project.getPluginManager().withPlugin("org.springframework.boot", ignored -> extension.getInputJar()
                .convention(project.getTasks().named("bootJar", Jar.class).flatMap(Jar::getArchiveFile)));

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSet main = java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        FileCollection libraries = main.getCompileClasspath().minus(main.getOutput())
                .plus(main.getRuntimeClasspath().minus(main.getOutput()));
        extension.getLibraries().from(libraries);

        TaskProvider<ArtifactProtectionTask> protect = project.getTasks().register(
                "protectArtifact", ArtifactProtectionTask.class, task -> {
                    task.setGroup("build");
                    task.setDescription("Protects the business artifact in build/protected.");
                    task.getProtectionEnabled().set(extension.getEnabled());
                    task.getObfuscationEnabled().set(extension.getObfuscationEnabled());
                    task.getSigningEnabled().set(extension.getSigningEnabled());
                    task.getPackages().set(extension.getPackages());
                    task.getInputJar().set(extension.getInputJar());
                    task.getOutputJar().set(extension.getOutputJar());
                    task.getMappingFile().set(extension.getMappingFile());
                    task.getSignatureFile().set(project.getLayout().file(extension.getOutputJar().map(output ->
                            new File(output.getAsFile().getParentFile(), output.getAsFile().getName() + ".sig"))));
                    task.getSignatureFile().disallowChanges();
                    task.getKeepRules().set(extension.getKeepRules());
                    task.getLibraries().from(extension.getLibraries());
                    task.getObfuscatorClasspath().from(extension.getObfuscatorClasspath());
                    task.getPrivateKeyFile().set(extension.getPrivateKeyFile());
                    task.getPublicKeyFile().set(extension.getPublicKeyFile());
                    task.getOutputs().upToDateWhen(ignored -> false);
                });
        project.getTasks().named("assemble").configure(task -> task.dependsOn(protect));
        createProtectedElements(project, extension, protect);
    }

    private void createProtectedElements(Project project, ArtifactProtectionExtension extension,
                                         TaskProvider<ArtifactProtectionTask> protect) {
        Configuration elements = project.getConfigurations().maybeCreate("protectedArtifactElements");
        elements.setCanBeResolved(false);
        elements.setCanBeConsumed(false);
        elements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE,
                project.getObjects().named(Usage.class, "artifact-protected"));
        elements.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE,
                project.getObjects().named(Category.class, Category.LIBRARY));
        elements.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
        elements.getAttributes().attribute(PROTECTED_ARTIFACT_ATTRIBUTE, true);
        project.afterEvaluate(ignored -> {
            if (extension.getEnabled().getOrElse(false)) {
                elements.setCanBeConsumed(true);
                elements.getOutgoing().artifact(extension.getOutputJar(), artifact -> artifact.builtBy(protect));
            }
        });
    }
}
