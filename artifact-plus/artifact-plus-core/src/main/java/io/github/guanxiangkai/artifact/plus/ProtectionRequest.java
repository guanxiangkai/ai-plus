package io.github.guanxiangkai.artifact.plus;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 业务制品构建契约；所有路径由构建工具解析，输出不得覆盖任一输入。
 *
 * @param input 完成 Boot 重打包后的输入 JAR
 * @param output 独立保护输出 JAR；伴随签名为此路径追加 .sig
 * @param mapping 内部排障用映射文件，不随业务制品分发
 * @param enabled 是否生成保护制品；关闭时清除配置的保护输出
 * @param obfuscate 是否混淆业务包内部成员
 * @param sign 是否对最终制品签名并立即验签
 * @param packages 业务包前缀，例如 com.example.business 或 com.example.business.**
 * @param keepRules 可选的额外 ProGuard 保留规则文件
 * @param libraries 仅供解析的业务依赖，不会被混淆或合并到输出
 * @param privateKey 外部 RSA 私钥文件，签名开启时必填
 * @param publicKey 外部可信 RSA 公钥文件，签名开启时必填
 * @param obfuscatorClasspath 独立 ProGuard CLI 进程的工具类路径
 */
public record ProtectionRequest(Path input, Path output, Path mapping, boolean enabled,
        boolean obfuscate, boolean sign, List<String> packages, Path keepRules,
        List<Path> libraries, Path privateKey, Path publicKey, List<Path> obfuscatorClasspath) {
    /** 校验必需路径，并防止调用方在处理期间改变集合。 */
    public ProtectionRequest {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(mapping, "mapping");
        packages = List.copyOf(packages);
        libraries = List.copyOf(libraries);
        obfuscatorClasspath = List.copyOf(obfuscatorClasspath);
    }
}
