package io.github.guanxiangkai.artifact.plus.signing;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * 制品验签命令行入口。
 */
public final class VerifyArtifact {

    private VerifyArtifact() {
    }

    /**
     * 按“制品、签名、公钥”顺序验证制品；失败时返回非零退出码，且不输出密钥内容。
     *
     * @param arguments 三个路径参数：制品、签名和公钥
     */
    public static void main(String[] arguments) {
        int exitCode = verify(arguments, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static int verify(String[] arguments, PrintStream error) {
        if (arguments == null || arguments.length != 3) {
            error.println("用法: VerifyArtifact <artifact> <signature> <public-key>");
            return 2;
        }
        try {
            ArtifactSignatures.verify(Path.of(arguments[0]), Path.of(arguments[1]), Path.of(arguments[2]));
            return 0;
        } catch (Exception exception) {
            error.println("制品签名验证失败");
            return 1;
        }
    }
}
