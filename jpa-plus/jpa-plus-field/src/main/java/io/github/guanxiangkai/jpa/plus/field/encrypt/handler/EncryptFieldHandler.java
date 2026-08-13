package io.github.guanxiangkai.jpa.plus.field.encrypt.handler;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;
import io.github.guanxiangkai.jpa.plus.core.field.BatchCapableFieldHandler;
import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.field.encrypt.annotation.Encrypt;
import io.github.guanxiangkai.jpa.plus.field.encrypt.enums.EncryptAlgorithm;
import io.github.guanxiangkai.jpa.plus.field.encrypt.enums.EncryptionAlgorithm;
import io.github.guanxiangkai.jpa.plus.field.encrypt.hutool.HutoolBacked;
import io.github.guanxiangkai.jpa.plus.field.encrypt.spi.EncryptKeyProvider;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 字段加密处理器
 *
 * <ul>
 *   <li>保存前（{@link #beforeSave}）：明文 → 密文（Base64 编码）</li>
 *   <li>查询后（{@link #afterQuery}）：密文 → 明文</li>
 * </ul>
 * </p>
 *
 * <h3>安全说明</h3>
 * <ul>
 *   <li>默认使用 {@link EncryptAlgorithm#AES_CBC}（确定性 CBC，IV 由 key 哈希派生），
 *       适合数据库字段加密（可按密文查询）</li>
 *   <li>国密 SM4 需引入 {@code cn.hutool:hutool-crypto}，
 *       通过 {@link EncryptionAlgorithm} 接口自定义枚举实现</li>
 * </ul>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class EncryptFieldHandler implements BatchCapableFieldHandler {

    private static final String VERSION_DELIMITER = ":";
    private static final String DEFAULT_KEY_VERSION = "v1";

    /**
     * 版本前缀格式：仅 "v" 后跟一个或多个数字（如 v1、v2、v10）视为有效版本号。
     * 防止包含 ":" 的普通字符串（URL、时间戳、IP:PORT 等）被误判为已加密密文。
     */
    private static final Pattern VERSION_PREFIX_PATTERN = Pattern.compile("^v\\d+$");

    private static final ClassValue<EncryptionAlgorithm> CUSTOM_ALGORITHM_CACHE = new ClassValue<>() {
        @Override
        protected EncryptionAlgorithm computeValue(Class<?> type) {
            return ReflectionUtils.instantiate(type.asSubclass(EncryptionAlgorithm.class));
        }
    };

    private final EncryptKeyProvider keyProvider;

    public EncryptFieldHandler(EncryptKeyProvider keyProvider) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "EncryptKeyProvider must not be null");
    }

    /**
     * 将用户配置的 key 字符串补齐或截断为目标字节长度。
     * 短于目标长度时 zero-pad；长于时截断。
     */
    private static byte[] deriveKey(String key, int size) {
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        return raw.length == size ? raw : Arrays.copyOf(raw, size);
    }

    /**
     * 确定性 IV 派生：IV = SHA-256(keyBytes)[0:16]。
     * 同一 key 永远派生出相同的 IV，使加密结果具确定性（可按密文查询）。
     * 若需随机 IV（更安全但不可查询），可通过自定义 {@link EncryptionAlgorithm} 实现覆盖此行为。
     */
    private static byte[] deriveIv(byte[] keyBytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(keyBytes);
            return Arrays.copyOf(hash, 16);
        } catch (Exception e) {
            return Arrays.copyOf(keyBytes, 16); // fallback
        }
    }

    private static String normalizeVersion(String keyVersion) {
        if (keyVersion == null || keyVersion.isBlank()) {
            return DEFAULT_KEY_VERSION;
        }
        return keyVersion.trim();
    }

    private static String toVersionedCipherText(String keyVersion, String encryptedPayload) {
        return normalizeVersion(keyVersion) + VERSION_DELIMITER + encryptedPayload;
    }

    // ─── 内部实现 ───

    private static CipherPayload parseCipherPayload(String encryptedText) {
        int delimiterIdx = encryptedText.indexOf(VERSION_DELIMITER);
        if (delimiterIdx <= 0 || delimiterIdx == encryptedText.length() - 1) {
            return new CipherPayload(null, encryptedText, false);
        }
        String version = encryptedText.substring(0, delimiterIdx).trim();
        String payload = encryptedText.substring(delimiterIdx + 1);
        // 仅 "v1:", "v2:", "v10:" 等格式视为已加密密文版本前缀；
        // 其余含 ":" 的字符串（URL、时间戳、IP:PORT 等）均视为明文，不跳过加密。
        if (version.isEmpty() || !VERSION_PREFIX_PATTERN.matcher(version).matches()) {
            return new CipherPayload(null, encryptedText, false);
        }
        return new CipherPayload(version, payload, true);
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean supports(Field field) {
        return field.isAnnotationPresent(Encrypt.class);
    }

    private EncryptionAlgorithm resolveAlgorithm(Encrypt annotation) {
        Class<? extends EncryptionAlgorithm> customClass = annotation.customAlgorithm();
        if (customClass != EncryptionAlgorithm.class) {
            return CUSTOM_ALGORITHM_CACHE.get(customClass);
        }
        return annotation.algorithm();
    }

    @Override
    public void beforeSave(Object entity, Field field) {
        try {
            Object value = ReflectionUtils.getFieldValue(entity, field);
            if (value instanceof String plainText) {
                // P0-17: Idempotency guard — skip if this value is already a versioned ciphertext.
                if (parseCipherPayload(plainText).versioned()) {
                    return;
                }
                EncryptionAlgorithm algo = resolveAlgorithm(field.getAnnotation(Encrypt.class));
                String keyVersion = normalizeVersion(keyProvider.getActiveVersion());
                String encrypted = doEncrypt(plainText, algo, keyProvider.getKeyByVersion(keyVersion));
                ReflectionUtils.setFieldValue(entity, field, toVersionedCipherText(keyVersion, encrypted));
            }
        } catch (Exception e) {
            throw new JpaPlusException("字段加密失败: field=" + field.getName(), e);
        }
    }

    @Override
    public void afterQuery(Object entity, Field field) {
        try {
            Object value = ReflectionUtils.getFieldValue(entity, field);
            if (value instanceof String encryptedText) {
                EncryptionAlgorithm algo = resolveAlgorithm(field.getAnnotation(Encrypt.class));
                ReflectionUtils.setFieldValue(entity, field, decryptCipherText(encryptedText, algo));
            }
        } catch (Exception e) {
            throw new JpaPlusException("字段解密失败: field=" + field.getName(), e);
        }
    }

    @Override
    public void beforeSaveBatch(List<?> entities, Field field) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        Encrypt annotation = field.getAnnotation(Encrypt.class);
        if (annotation == null) {
            log.warn("[jpa-plus] @Encrypt annotation not found on field '{}', skipping batch encryption", field.getName());
            return;
        }
        EncryptionAlgorithm algorithm = resolveAlgorithm(annotation);
        String keyVersion = normalizeVersion(keyProvider.getActiveVersion());
        String key = keyProvider.getKeyByVersion(keyVersion);

        for (Object entity : entities) {
            try {
                Object value = ReflectionUtils.getFieldValue(entity, field);
                if (value instanceof String plainText) {
                    // P0-17: Idempotency guard — skip if already a versioned ciphertext.
                    if (parseCipherPayload(plainText).versioned()) {
                        continue;
                    }
                    String encrypted = doEncrypt(plainText, algorithm, key);
                    ReflectionUtils.setFieldValue(entity, field, toVersionedCipherText(keyVersion, encrypted));
                }
            } catch (Exception e) {
                throw new JpaPlusException("批量字段加密失败: field=" + field.getName(), e);
            }
        }
    }

    @Override
    public void afterQueryBatch(List<?> entities, Field field) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        Encrypt annotation = field.getAnnotation(Encrypt.class);
        if (annotation == null) {
            log.warn("[jpa-plus] @Encrypt annotation not found on field '{}', skipping batch decryption", field.getName());
            return;
        }
        EncryptionAlgorithm algorithm = resolveAlgorithm(annotation);

        for (Object entity : entities) {
            try {
                Object value = ReflectionUtils.getFieldValue(entity, field);
                if (value instanceof String encryptedText) {
                    ReflectionUtils.setFieldValue(entity, field, decryptCipherText(encryptedText, algorithm));
                }
            } catch (Exception e) {
                throw new JpaPlusException("批量字段解密失败: field=" + field.getName(), e);
            }
        }
    }

    private String doEncrypt(String plainText, EncryptionAlgorithm algo, String encryptKey) throws Exception {
        byte[] keyBytes = deriveKey(encryptKey, algo.keySize());

        // P0-13: Route to hutool engine via HutoolBacked interface (no unsafe cast to HutoolEncryptAlgorithm).
        if (algo instanceof HutoolBacked hutool) {
            return hutool.encryptBase64(plainText, keyBytes);
        }

        // JCE 内置路径（AES/ECB、AES/CBC 等标准算法）
        Cipher cipher = Cipher.getInstance(algo.transformation());
        SecretKeySpec ks = new SecretKeySpec(keyBytes, algo.algorithmName());
        if (algo.needsIv()) {
            cipher.init(Cipher.ENCRYPT_MODE, ks, new IvParameterSpec(deriveIv(keyBytes)));
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, ks);
        }
        return Base64.getEncoder().encodeToString(
                cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
    }

    private String doDecrypt(String encryptedText, EncryptionAlgorithm algo, String decryptKey) throws Exception {
        byte[] keyBytes = deriveKey(decryptKey, algo.keySize());

        // P0-13: Route to hutool engine via HutoolBacked interface (no unsafe cast).
        if (algo instanceof HutoolBacked hutool) {
            return hutool.decryptStr(encryptedText, keyBytes);
        }

        // JCE 内置路径
        Cipher cipher = Cipher.getInstance(algo.transformation());
        SecretKeySpec ks = new SecretKeySpec(keyBytes, algo.algorithmName());
        if (algo.needsIv()) {
            cipher.init(Cipher.DECRYPT_MODE, ks, new IvParameterSpec(deriveIv(keyBytes)));
        } else {
            cipher.init(Cipher.DECRYPT_MODE, ks);
        }
        return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedText)),
                StandardCharsets.UTF_8);
    }

    private String decryptCipherText(String encryptedText, EncryptionAlgorithm algorithm) throws Exception {
        CipherPayload payload = parseCipherPayload(encryptedText);
        if (!payload.versioned()) {
            throw new JpaPlusException("字段密文缺少版本前缀，2.0 起仅支持形如 'v1:<cipherText>' 的版本化密文");
        }
        return doDecrypt(payload.cipherText(), algorithm, keyProvider.getKeyByVersion(payload.version()));
    }

    private record CipherPayload(String version, String cipherText, boolean versioned) {
    }
}
