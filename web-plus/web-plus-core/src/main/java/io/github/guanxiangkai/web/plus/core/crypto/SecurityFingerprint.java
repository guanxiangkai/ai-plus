package io.github.guanxiangkai.web.plus.core.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 生成安全相关材料的固定长度 SHA-256 指纹。
 *
 * <p>该类型适合避免在缓存键、撤销记录和诊断信息中保存高熵令牌明文，但不负责令牌真伪校验。
 * 低熵用户名、手机号或 IP 的普通哈希仍可能被枚举，因此不能把其结果宣称为匿名数据。</p>
 */
public final class SecurityFingerprint {

    private SecurityFingerprint() {
        throw new UnsupportedOperationException("这是一个效用类，无法实例化");
    }

    /**
     * 返回输入材料的 SHA-256 十六进制指纹。
     *
     * @param material 待处理的安全材料，不能为 {@code null}
     * @return 64 个小写十六进制字符
     * @throws NullPointerException 输入材料为空时抛出
     */
    public static String sha256(String material) {
        Objects.requireNonNull(material, "安全指纹输入不能为空");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
