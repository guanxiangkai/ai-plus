package io.github.guanxiangkai.web.plus.license;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * 从本地 PEM 或 DER 文件载入许可证 RSA 密钥。
 *
 * <p>公钥为 X.509 SubjectPublicKeyInfo，私钥为 PKCS#8。该类不生成密钥，也不记录密钥材料。</p>
 */
public final class LicenseKeys {
    private static final int MAX_KEY_FILE_BYTES = 64 * 1024;

    private LicenseKeys() {
    }

    /**
     * 读取并校验用于验签的 X.509 SPKI RSA 公钥。
     *
     * @param path 公钥 PEM 或 DER 文件
     * @return 至少 3072 位的 RSA 公钥
     */
    public static PublicKey readPublicKey(Path path) {
        return readKey(path, false, PublicKey.class);
    }

    /**
     * 读取并校验仅供签发端使用的 PKCS#8 RSA 私钥。
     *
     * @param path 私钥 PEM 或 DER 文件
     * @return 至少 3072 位的 RSA 私钥
     */
    public static PrivateKey readPrivateKey(Path path) {
        return readKey(path, true, PrivateKey.class);
    }

    static void requireRsa3072(java.security.Key key) {
        if (!(key instanceof RSAKey rsaKey) || rsaKey.getModulus().bitLength() < 3072) {
            throw new LicenseException("许可证密钥必须是至少 3072 位的 RSA 密钥");
        }
    }

    private static <T extends java.security.Key> T readKey(Path path, boolean privateKey, Class<T> keyType) {
        Objects.requireNonNull(path, "path 不能为空");
        try {
            byte[] encoded;
            try (InputStream input = Files.newInputStream(path)) {
                encoded = input.readNBytes(MAX_KEY_FILE_BYTES + 1);
            }
            encoded = decodePemOrDer(encoded, privateKey);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            java.security.Key key = privateKey
                    ? factory.generatePrivate(new PKCS8EncodedKeySpec(encoded))
                    : factory.generatePublic(new X509EncodedKeySpec(encoded));
            requireRsa3072(key);
            return keyType.cast(key);
        } catch (IOException | java.security.GeneralSecurityException | IllegalArgumentException exception) {
            throw new LicenseException("许可证密钥读取失败");
        }
    }

    private static byte[] decodePemOrDer(byte[] source, boolean privateKey) {
        if (source.length == 0 || source.length > MAX_KEY_FILE_BYTES) {
            throw new LicenseException("许可证密钥文件大小无效");
        }
        String text = new String(source, java.nio.charset.StandardCharsets.US_ASCII);
        if (!text.startsWith("-----BEGIN")) {
            return source;
        }
        String normalized = text.replaceAll("\\s", "");
        String begin = privateKey ? "-----BEGINPRIVATEKEY-----" : "-----BEGINPUBLICKEY-----";
        String end = privateKey ? "-----ENDPRIVATEKEY-----" : "-----ENDPUBLICKEY-----";
        if (!normalized.startsWith(begin) || !normalized.endsWith(end)) {
            throw new LicenseException("许可证 PEM 格式无效");
        }
        try {
            return Base64.getDecoder().decode(normalized.substring(begin.length(), normalized.length() - end.length()));
        } catch (IllegalArgumentException exception) {
            throw new LicenseException("许可证 PEM 编码无效");
        }
    }
}
