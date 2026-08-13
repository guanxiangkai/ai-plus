package io.github.guanxiangkai.jpa.plus.field.encrypt.hutool;

/**
 * 标记接口：表示此 {@link io.github.guanxiangkai.jpa.plus.field.encrypt.enums.EncryptionAlgorithm} 由 hutool-crypto 驱动。
 *
 * <p>{@link io.github.guanxiangkai.jpa.plus.field.encrypt.handler.EncryptFieldHandler} 检测到此标记后
 * 自动路由至 hutool 引擎。实现类无需经过内置 JCE 路径。</p>
 *
 * <p>实现此接口的类必须提供 {@code encryptBase64} 和 {@code decryptStr} 方法。
 * 不允许仅实现标记而不提供加密方法——如需自定义，请继承 {@code HutoolEncryptAlgorithm}
 * 或直接实现这两个方法。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface HutoolBacked {

    /**
     * 使用 hutool 引擎加密并返回 Base64 密文。
     *
     * @param plainText 明文字符串
     * @param keyBytes  原始密钥字节（已由 EncryptFieldHandler 按算法要求填充/截断至正确长度）
     * @return Base64 编码的密文
     */
    String encryptBase64(String plainText, byte[] keyBytes);

    /**
     * 使用 hutool 引擎解密 Base64 密文并返回明文。
     *
     * @param cipherBase64 Base64 编码的密文
     * @param keyBytes     原始密钥字节
     * @return 解密后的明文字符串
     */
    String decryptStr(String cipherBase64, byte[] keyBytes);
}

