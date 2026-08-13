package io.github.guanxiangkai.jpa.plus.field.encrypt.enums;

/**
 * 内置加密算法枚举
 *
 * <h3>算法说明</h3>
 * <table border="1">
 *   <tr><th>枚举</th><th>模式</th><th>安全性</th><th>说明</th></tr>
 *   <tr><td>AES_CBC</td><td>CBC + 确定性IV</td><td>⚠️ 确定性（可查询）</td>
 *       <td>IV 由 key 哈希派生，加密结果与明文/key 绑定。同一明文总产生相同密文，
 *           可按密文查询，但面临频率分析风险。<b>仅在需要按加密字段查询时使用。</b></td></tr>
 *   <tr><td>AES_CBC_256</td><td>CBC-256 + 确定性IV</td><td>⚠️ 确定性（可查询）</td>
 *       <td>与 AES_CBC 同等确定性，密钥更长（256位），适合高安全需求的可查询场景。</td></tr>
 * </table>
 *
 * <h3>安全提示</h3>
 * <p>{@code AES_CBC} 和 {@code AES_CBC_256} 使用<b>确定性 IV</b>（IV = SHA-256(key)[0:16]），
 * 这意味着相同的明文与相同的密钥总会产生相同的密文。
 * 这一特性在数据库字段加密场景中是<b>有意为之</b>（允许 {@code WHERE cipher_col = ?} 查询），
 * 但会暴露明文的频率分布（与 AES/ECB 类似），在高价值数据场景下需谨慎评估风险。
 * 若数据不需要按密文查询，请通过 {@link EncryptionAlgorithm} 接口自定义实现使用随机 IV。</p>
 *
 * <h3>国密 SM4</h3>
 * <p>SM4 需要 hutool-crypto 支持，请实现 {@link EncryptionAlgorithm} 接口并使用 hutool-crypto 驱动。
 * 参考 {@code HutoolEncryptAlgorithm}（在 classpath 含 hutool-crypto 时可用）。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum EncryptAlgorithm implements EncryptionAlgorithm {

    /**
     * AES/CBC/PKCS5Padding + 确定性 IV（IV = SHA-256(key)[0:16]）。
     *
     * <p><b>安全注意事项：</b>使用确定性 IV 意味着相同的明文 + 相同的密钥总是产生
     * 相同的密文，适合<b>按加密字段查询</b>的场景（如 {@code WHERE email_cipher = ?}），
     * 但会暴露明文的频率分布。若不需要密文查询，建议使用随机 IV 方案。</p>
     */
    AES_CBC("AES", "AES/CBC/PKCS5Padding", true, 16),

    /**
     * AES-256/CBC/PKCS5Padding + 确定性 IV，密钥长度 32 字节，安全性更高。
     *
     * <p>与 {@link #AES_CBC} 具有相同的确定性 IV 特性及安全权衡，仅密钥长度不同。</p>
     */
    AES_CBC_256("AES", "AES/CBC/PKCS5Padding", true, 32);

    // ─── 注解用字符串常量（供 @Encrypt 注解默认值引用） ───
    public static final String AES_CBC_NAME = "AES_CBC";

    private final String algorithmName;
    private final String transformation;
    private final boolean needsIv;
    private final int keySize;

    EncryptAlgorithm(String algorithmName, String transformation, boolean needsIv, int keySize) {
        this.algorithmName = algorithmName;
        this.transformation = transformation;
        this.needsIv = needsIv;
        this.keySize = keySize;
    }

    @Override
    public String algorithmName() {
        return algorithmName;
    }

    @Override
    public String transformation() {
        return transformation;
    }

    @Override
    public boolean needsIv() {
        return needsIv;
    }

    @Override
    public int keySize() {
        return keySize;
    }
}
