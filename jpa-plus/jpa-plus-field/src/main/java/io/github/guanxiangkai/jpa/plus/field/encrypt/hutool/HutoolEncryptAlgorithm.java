package io.github.guanxiangkai.jpa.plus.field.encrypt.hutool;

import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.symmetric.AES;
import io.github.guanxiangkai.jpa.plus.field.encrypt.enums.EncryptionAlgorithm;

import java.security.MessageDigest;
import java.util.Arrays;

/**
 * 基于 hutool-crypto 的国密/扩展加密算法枚举
 *
 * <p>当 {@code cn.hutool:hutool-crypto} 在 classpath 时可用。
 * 提供 AES（hutool 强化版）和国密 SM4 算法支持。</p>
 *
 * <h3>引入依赖</h3>
 * <pre>{@code
 * // Gradle
 * implementation("cn.hutool:hutool-crypto:5.8.x")
 *
 * // Maven
 * <dependency>
 *     <groupId>cn.hutool</groupId>
 *     <artifactId>hutool-crypto</artifactId>
 *     <version>5.8.x</version>
 * </dependency>
 * }</pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Encrypt(customAlgorithm = HutoolEncryptAlgorithm.SM4_ECB.class)
 * private String idCard;
 *
 * @Encrypt(customAlgorithm = HutoolEncryptAlgorithm.SM4_CBC.class)
 * private String bankCard;
 * }</pre>
 *
 * <p><b>注意：</b>此类使用 hutool-crypto 编译期 API，不可在 hutool 不存在时被加载。
 * 通过 {@code @Encrypt(customAlgorithm = HutoolEncryptAlgorithm.SM4_ECB.class)} 使用时，
 * 需确保 hutool-crypto 在运行时 classpath 中。</p>
 *
 * <h3>与 EncryptFieldHandler 的集成</h3>
 * <p>{@code HutoolEncryptAlgorithm} 实现了 {@link HutoolBacked} 标记接口，
 * {@code EncryptFieldHandler} 检测到此标记后自动路由至 hutool 引擎，
 * 完全绕过内置 JCE 实现。</p>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.jpa.plus.field.encrypt.enums.EncryptAlgorithm
 * @since 1.0.0
 */
public enum HutoolEncryptAlgorithm implements EncryptionAlgorithm, HutoolBacked {

    /**
     * SM4/ECB/PKCS5Padding —— 国密对称加密，安全性等同 AES-128
     */
    SM4_ECB {
        @Override
        public String encryptBase64(String plainText, byte[] keyBytes) {
            return SmUtil.sm4(keyBytes).encryptBase64(plainText);
        }

        @Override
        public String decryptStr(String cipherBase64, byte[] keyBytes) {
            return SmUtil.sm4(keyBytes).decryptStr(cipherBase64);
        }

        @Override
        public String algorithmName() {
            return "SM4";
        }

        @Override
        public String transformation() {
            return "SM4/ECB/PKCS5Padding";
        }

        @Override
        public int keySize() {
            return 16;
        }
    },

    /**
     * SM4/CBC/PKCS5Padding + 确定性 IV（IV = SHA-256(key)[0:16]）
     */
    SM4_CBC {
        @Override
        public String encryptBase64(String plainText, byte[] keyBytes) {
            byte[] iv = deriveIv(keyBytes);
            return SmUtil.sm4(keyBytes).setIv(iv).encryptBase64(plainText);
        }

        @Override
        public String decryptStr(String cipherBase64, byte[] keyBytes) {
            byte[] iv = deriveIv(keyBytes);
            return SmUtil.sm4(keyBytes).setIv(iv).decryptStr(cipherBase64);
        }

        @Override
        public String algorithmName() {
            return "SM4";
        }

        @Override
        public String transformation() {
            return "SM4/CBC/PKCS5Padding";
        }

        @Override
        public boolean needsIv() {
            return true;
        }

        @Override
        public int keySize() {
            return 16;
        }
    },

    /**
     * AES/CBC/PKCS5Padding via hutool（与内置 AES_CBC 等效，但通过 hutool 驱动）
     */
    AES_HUTOOL {
        @Override
        public String encryptBase64(String plainText, byte[] keyBytes) {
            byte[] iv = deriveIv(keyBytes);
            return new AES(cn.hutool.crypto.Mode.CBC, cn.hutool.crypto.Padding.PKCS5Padding,
                    keyBytes, iv).encryptBase64(plainText);
        }

        @Override
        public String decryptStr(String cipherBase64, byte[] keyBytes) {
            byte[] iv = deriveIv(keyBytes);
            return new AES(cn.hutool.crypto.Mode.CBC, cn.hutool.crypto.Padding.PKCS5Padding,
                    keyBytes, iv).decryptStr(cipherBase64);
        }

        @Override
        public String algorithmName() {
            return "AES";
        }

        @Override
        public String transformation() {
            return "AES/CBC/PKCS5Padding";
        }

        @Override
        public boolean needsIv() {
            return true;
        }

        @Override
        public int keySize() {
            return 16;
        }
    };

    // ─── hutool 路由方法（子类必须实现） ───

    protected static byte[] deriveIv(byte[] keyBytes) {
        try {
            return Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(keyBytes), 16);
        } catch (Exception e) {
            return Arrays.copyOf(keyBytes, 16);
        }
    }

    /**
     * 使用 hutool 加密并返回 Base64 密文
     */
    public abstract String encryptBase64(String plainText, byte[] keyBytes);

    // ─── 工具方法 ───

    /**
     * 使用 hutool 解密 Base64 密文并返回明文
     */
    public abstract String decryptStr(String cipherBase64, byte[] keyBytes);
}

