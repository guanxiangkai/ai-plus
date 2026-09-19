package io.github.guanxiangkai.artifact.plus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 以有界诊断和有界回收运行外部构建工具。 */
final class ToolProcess {

    private static final int MAXIMUM_DIAGNOSTIC_BYTES = 32 * 1024;
    private static final int HALF_DIAGNOSTIC_BYTES = MAXIMUM_DIAGNOSTIC_BYTES / 2;
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);

    private ToolProcess() {
    }

    /**
     * 运行不经 Shell 解释的工具命令。
     *
     * @param command 已拆分的可执行文件及参数
     * @param timeout 工具正常执行的最长时间
     * @throws IOException 启动失败、超时、中断、诊断读取失败或工具返回非零状态时抛出
     */
    static void run(List<String> command, Duration timeout) throws IOException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(timeout, "timeout");
        if (command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("工具命令不能为空");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("工具超时时间必须为正数");
        }

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            process.getOutputStream().close();
        } catch (IOException exception) {
            boolean interrupted = terminate(process);
            closeStreams(process);
            if (interrupted) Thread.currentThread().interrupt();
            throw exception;
        }
        BoundedDiagnostic diagnostic = new BoundedDiagnostic();
        AtomicReference<IOException> readerFailure = new AtomicReference<>();
        Thread reader = Thread.ofVirtual().start(() -> readDiagnostic(process.getInputStream(), diagnostic, readerFailure));
        IOException failure = null;
        boolean interrupted = false;
        boolean stopReader = false;
        Integer exitCode = null;
        try {
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                interrupted |= terminate(process);
                stopReader = true;
                failure = new IOException("工具进程执行超时：" + timeout);
            } else {
                exitCode = process.exitValue();
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            interrupted |= terminate(process);
            stopReader = true;
            failure = new IOException("等待工具进程时被中断", exception);
        } finally {
            if (process.isAlive()) {
                interrupted |= terminate(process);
                stopReader = true;
            }
            if (stopReader) {
                closeStreams(process);
            }
            try {
                reader.join(TERMINATION_TIMEOUT);
            } catch (InterruptedException exception) {
                interrupted = true;
                stopReader = true;
                closeStreams(process);
                if (failure == null) {
                    failure = new IOException("等待工具诊断线程结束时被中断", exception);
                } else {
                    failure.addSuppressed(exception);
                }
            }
            if (reader.isAlive()) {
                stopReader = true;
                closeStreams(process);
                reader.interrupt();
                IOException readerTimeout = new IOException("工具诊断线程未能及时结束");
                if (failure == null) {
                    failure = readerTimeout;
                } else {
                    failure.addSuppressed(readerTimeout);
                }
            }
            IOException readFailure = readerFailure.get();
            if (readFailure != null && !stopReader) {
                if (failure == null) {
                    failure = readFailure;
                } else {
                    failure.addSuppressed(readFailure);
                }
            }
            if (failure == null && exitCode != null && exitCode != 0) {
                failure = new IOException("工具进程异常退出，状态码 " + exitCode + diagnostic.suffix());
            }
            if (!reader.isAlive()) {
                closeStreams(process);
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void readDiagnostic(InputStream input, BoundedDiagnostic diagnostic,
                                       AtomicReference<IOException> failure) {
        try (input) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) {
                diagnostic.append(buffer, count);
            }
        } catch (IOException exception) {
            failure.compareAndSet(null, exception);
        }
    }

    private static boolean terminate(Process process) {
        if (!process.isAlive()) return false;
        process.destroyForcibly();
        try {
            process.waitFor(TERMINATION_TIMEOUT);
        } catch (InterruptedException exception) {
            return true;
        }
        return false;
    }

    private static void closeStreams(Process process) {
        try {
            process.getInputStream().close();
        } catch (IOException ignored) {
            // 进程的退出状态仍是此次调用的主要失败原因。
        }
        try {
            process.getErrorStream().close();
        } catch (IOException ignored) {
            // 已合并到标准输出，关闭失败不应覆盖工具的原始错误。
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // 标准输入在启动后已关闭。
        }
    }

    /** 仅保留工具输出的开始和结束片段，避免异常诊断耗尽内存。 */
    private static final class BoundedDiagnostic {
        private final ByteArrayOutputStream head = new ByteArrayOutputStream(HALF_DIAGNOSTIC_BYTES);
        private final byte[] tail = new byte[HALF_DIAGNOSTIC_BYTES];
        private long totalBytes;
        private int tailStart;
        private int tailSize;

        private void append(byte[] source, int length) {
            int headLength = Math.min(length, HALF_DIAGNOSTIC_BYTES - head.size());
            if (headLength > 0) head.write(source, 0, headLength);
            for (int index = headLength; index < length; index++) {
                tail[(tailStart + tailSize) % tail.length] = source[index];
                if (tailSize < tail.length) {
                    tailSize++;
                } else {
                    tailStart = (tailStart + 1) % tail.length;
                }
            }
            totalBytes += length;
        }

        private String suffix() {
            if (totalBytes == 0) return "";
            byte[] beginning = head.toByteArray();
            byte[] ending = new byte[tailSize];
            for (int index = 0; index < tailSize; index++) {
                ending[index] = tail[(tailStart + index) % tail.length];
            }
            if (totalBytes <= MAXIMUM_DIAGNOSTIC_BYTES) {
                return ":\n" + new String(beginning, java.nio.charset.StandardCharsets.UTF_8)
                        + new String(ending, java.nio.charset.StandardCharsets.UTF_8);
            }
            return ":\n" + new String(beginning, java.nio.charset.StandardCharsets.UTF_8)
                    + "\n… tool output truncated …\n"
                    + new String(ending, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
