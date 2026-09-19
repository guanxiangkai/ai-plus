package io.github.guanxiangkai.artifact.plus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证外部工具进程的输出背压与回收边界。 */
class ToolProcessTest {

    @Test
    @Timeout(10)
    void completesNormallyWithoutPublishingDiagnostics() {
        assertDoesNotThrow(() -> ToolProcess.run(command("ok"), Duration.ofSeconds(3)));
    }

    @Test
    @Timeout(10)
    void drainsLargeOutputFromSuccessfulTool() {
        assertDoesNotThrow(() -> ToolProcess.run(command("flood-ok"), Duration.ofSeconds(3)));
    }

    @Test
    @Timeout(10)
    void reportsBoundedDiagnosticForNonZeroExit() {
        IOException failure = assertThrows(IOException.class,
                () -> ToolProcess.run(command("fail"), Duration.ofSeconds(3)));
        assertTrue(failure.getMessage().contains("failure-marker"));
        assertFalse(failure.getMessage().contains(javaExecutable()));
    }

    @Test
    @Timeout(10)
    void drainsLargeOutputWithoutDeadlockAndKeepsBothEnds() {
        IOException failure = assertThrows(IOException.class,
                () -> ToolProcess.run(command("flood"), Duration.ofSeconds(3)));
        assertTrue(failure.getMessage().contains("head-marker"));
        assertTrue(failure.getMessage().contains("tail-marker"));
        assertTrue(failure.getMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 33 * 1024);
    }

    @Test
    @Timeout(10)
    void terminatesTimedOutToolWithinBound() {
        IOException failure = assertThrows(IOException.class,
                () -> ToolProcess.run(command("block"), Duration.ofMillis(100)));
        assertTrue(failure.getMessage().contains("超时"));
    }

    @Test
    @Timeout(10)
    void restoresInterruptFlagAndReturnsWithinBound() throws InterruptedException {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                ToolProcess.run(command("block"), Duration.ofSeconds(5));
            } catch (Throwable exception) {
                failure.set(exception);
                interrupted.set(Thread.currentThread().isInterrupted());
            } finally {
                finished.countDown();
            }
        });
        worker.interrupt();

        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertTrue(failure.get() instanceof IOException);
        assertTrue(interrupted.get());
    }

    private static List<String> command(String mode) {
        return List.of(javaExecutable(), "-cp", testClasses(), Fixture.class.getName(), mode);
    }

    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static String testClasses() {
        try {
            return Path.of(Fixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Cannot locate fixture classes", exception);
        }
    }

    /** 由独立 JVM 启动的无第三方依赖测试工具。 */
    public static final class Fixture {
        private Fixture() {
        }

        /** 根据参数输出、失败或持续等待。 */
        public static void main(String[] arguments) throws Exception {
            switch (arguments[0]) {
                case "ok" -> System.out.print("normal-marker");
                case "fail" -> {
                    System.err.print("failure-marker");
                    System.exit(7);
                }
                case "flood" -> {
                    System.out.print("head-marker");
                    byte[] content = "x".repeat(256 * 1024).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                    System.out.write(content);
                    System.out.print("tail-marker");
                    System.exit(9);
                }
                case "flood-ok" -> {
                    System.out.print("head-marker");
                    byte[] content = "x".repeat(256 * 1024).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                    System.out.write(content);
                    System.out.print("tail-marker");
                }
                case "block" -> new CountDownLatch(1).await();
                default -> throw new IllegalArgumentException("unknown fixture mode");
            }
        }
    }
}
