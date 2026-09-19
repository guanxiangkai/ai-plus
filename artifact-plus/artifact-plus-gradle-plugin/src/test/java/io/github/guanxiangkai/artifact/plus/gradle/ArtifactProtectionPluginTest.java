package io.github.guanxiangkai.artifact.plus.gradle;

import io.github.guanxiangkai.artifact.plus.signing.ArtifactSignatures;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.concurrent.TimeUnit;

import static org.gradle.testkit.runner.TaskOutcome.FAILED;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 Gradle 工程验证业务制品保护插件。 */
class ArtifactProtectionPluginTest {

    @TempDir
    Path projectDirectory;

    @Test
    void signsPlainJavaJarAndSupportsConfigurationCache() throws Exception {
        writeSettings();
        writeJavaSource("package sample; public class App { public static void main(String[] args) { } }");
        KeyPair keys = createKeys();
        Files.write(projectDirectory.resolve("private.der"), keys.getPrivate().getEncoded());
        Files.write(projectDirectory.resolve("public.der"), keys.getPublic().getEncoded());
        writeBuild("""
                plugins {
                    id 'java'
                    id 'io.github.guanxiangkai.artifact-protection'
                }
                artifactProtection {
                    enabled = true
                    signingEnabled = true
                    privateKeyFile = layout.projectDirectory.file('private.der')
                    publicKeyFile = layout.projectDirectory.file('public.der')
                }
                """);

        BuildResult first = run("assemble", "--configuration-cache");
        BuildResult second = run("assemble", "--configuration-cache");

        assertEquals(SUCCESS, first.task(":protectArtifact").getOutcome());
        assertEquals(SUCCESS, second.task(":protectArtifact").getOutcome());
        assertTrue(Files.isRegularFile(protectedJar()));
        assertTrue(Files.isRegularFile(signature()));
        ArtifactSignatures.verify(protectedJar(), signature(), projectDirectory.resolve("public.der"));
        assertTrue(second.getOutput().contains("Reusing configuration cache"));
    }

    @Test
    void disabledProtectionRemovesPreviousOutputs() throws Exception {
        writeSettings();
        writeJavaSource("package sample; public class App { }");
        KeyPair keys = createKeys();
        Files.write(projectDirectory.resolve("private.der"), keys.getPrivate().getEncoded());
        Files.write(projectDirectory.resolve("public.der"), keys.getPublic().getEncoded());
        writeBuild(signedBuild());
        run("protectArtifact");
        assertTrue(Files.isRegularFile(protectedJar()));

        writeBuild(enabledBuild(false));
        BuildResult result = run("protectArtifact");

        assertEquals(SUCCESS, result.task(":protectArtifact").getOutcome());
        assertFalse(Files.exists(protectedJar()));
        assertFalse(Files.exists(signature()));
        assertFalse(Files.exists(mapping()));
    }

    @Test
    void signingWithoutKeysFailsAssemble() throws Exception {
        writeSettings();
        writeJavaSource("package sample; public class App { }");
        writeBuild("""
                plugins {
                    id 'java'
                    id 'io.github.guanxiangkai.artifact-protection'
                }
                artifactProtection {
                    enabled = true
                    signingEnabled = true
                }
                """);

        BuildResult result = runAndFail("assemble");

        assertNotNull(result.task(":protectArtifact"));
        assertEquals(FAILED, result.task(":protectArtifact").getOutcome());
        assertTrue(result.getOutput().contains("Failed to protect artifact"));
    }

    @Test
    void enabledProtectionWithoutAnOperationFailsAssemble() throws Exception {
        writeSettings();
        writeJavaSource("package sample; public class App { }");
        writeBuild(enabledBuild(true));

        BuildResult result = runAndFail("assemble");

        assertNotNull(result.task(":protectArtifact"));
        assertEquals(FAILED, result.task(":protectArtifact").getOutcome());
    }

    @Test
    void protectsBootJarThatCanStart() throws Exception {
        writeSettings();
        KeyPair keys = createKeys();
        Files.write(projectDirectory.resolve("private.der"), keys.getPrivate().getEncoded());
        Files.write(projectDirectory.resolve("public.der"), keys.getPublic().getEncoded());
        Path source = projectDirectory.resolve("src/main/java/sample/Application.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package sample;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class Application {
                    public static void main(String[] args) {
                        var context = SpringApplication.run(Application.class, args);
                        System.out.println(Marker.value());
                        SpringApplication.exit(context);
                    }

                    private static final class Marker {
                        private static String value() {
                            return "protected-boot-marker";
                        }
                    }
                }
                """);
        writeBuild("""
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '4.1.1'
                    id 'io.github.guanxiangkai.artifact-protection'
                }
                repositories { mavenCentral() }
                dependencies { implementation 'org.springframework.boot:spring-boot-starter:4.1.1' }
                artifactProtection {
                    enabled = true
                    obfuscationEnabled = true
                    signingEnabled = true
                    packages = ['sample']
                    privateKeyFile = layout.projectDirectory.file('private.der')
                    publicKeyFile = layout.projectDirectory.file('public.der')
                }
                """);

        BuildResult result = run("assemble");
        Path log = projectDirectory.resolve("application.log");
        Process process = new ProcessBuilder(javaExecutable(), "-jar", protectedJar().toString())
                .directory(projectDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("保护后的 Boot 应用未在限定时间内退出");
        }
        String output = Files.readString(log);
        int exit = process.exitValue();

        assertEquals(SUCCESS, result.task(":protectArtifact").getOutcome());
        assertTrue(Files.isRegularFile(protectedJar()));
        assertTrue(Files.isRegularFile(signature()));
        assertEquals(0, exit, output);
        ArtifactSignatures.verify(protectedJar(), signature(), projectDirectory.resolve("public.der"));
        assertTrue(output.contains("protected-boot-marker"), output);
    }

    private void writeSettings() throws IOException {
        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'test-app'\n");
    }

    private void writeJavaSource(String source) throws IOException {
        Path java = projectDirectory.resolve("src/main/java/sample/App.java");
        Files.createDirectories(java.getParent());
        Files.writeString(java, source);
    }

    private void writeBuild(String content) throws IOException {
        Files.writeString(projectDirectory.resolve("build.gradle"), content);
    }

    private String enabledBuild(boolean enabled) {
        return """
                plugins {
                    id 'java'
                    id 'io.github.guanxiangkai.artifact-protection'
                }
                artifactProtection { enabled = %s }
                """.formatted(enabled);
    }

    private String signedBuild() {
        return """
                plugins {
                    id 'java'
                    id 'io.github.guanxiangkai.artifact-protection'
                }
                artifactProtection {
                    enabled = true
                    signingEnabled = true
                    privateKeyFile = layout.projectDirectory.file('private.der')
                    publicKeyFile = layout.projectDirectory.file('public.der')
                }
                """;
    }

    private BuildResult run(String... arguments) {
        return runner(arguments).build();
    }

    private BuildResult runAndFail(String... arguments) {
        return runner(arguments).buildAndFail();
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput();
    }

    private Path protectedJar() {
        return projectDirectory.resolve("build/protected/test-app.jar");
    }

    private Path signature() {
        return projectDirectory.resolve("build/protected/test-app.jar.sig");
    }

    private Path mapping() {
        return projectDirectory.resolve("build/protected/mapping.txt");
    }

    private KeyPair createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        return generator.generateKeyPair();
    }

    private String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
