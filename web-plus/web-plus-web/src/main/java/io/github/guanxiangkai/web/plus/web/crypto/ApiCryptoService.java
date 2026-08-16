package io.github.guanxiangkai.web.plus.web.crypto;

import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.symmetric.SM4;
import io.github.guanxiangkai.web.plus.web.properties.ApiCryptoProperties;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * API 加解密服务。
 *
 * <p>支持 {@code AES_GCM_V1} 与 {@code SM4_CBC_SM3_V1}。
 * 请求方向使用 {@code request.key} 解密，响应方向使用 {@code response.key} 加密。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class ApiCryptoService {

    public static final String CRYPTO_HEADER = "X-Api-Crypto";
    public static final String CRYPTO_KEY_ID_HEADER = "X-Api-Crypto-Key-Id";
    public static final String CRYPTO_QUERY_PARAM = "__api_crypto";

    private static final String VERSION = "2";
    private static final String SM4_KDF_DOMAIN = "web-plus:api-crypto:sm4:kdf:v2";
    private static final String SM3_MAC_DOMAIN = "web-plus:api-crypto:sm3:mac:v2";
    private static final String SM4_TAG_DOMAIN = "web-plus:api-crypto:sm4-cbc-sm3:tag:v2";
    private static final int AES_GCM_TAG_BITS = 128;

    private final ApiCryptoProperties properties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiCryptoService(ApiCryptoProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        validateProperties(properties);
    }

    public boolean requestEnabled() {
        return properties.requestEnabled();
    }

    public boolean responseEnabled() {
        return properties.responseEnabled();
    }

    public String responseStrategyHeader() {
        return properties.getStrategy().name();
    }

    public String responseKeyId() {
        return normalizedKeyId(properties.getResponse(), "response");
    }

    /**
     * 从 JSON 对象、JSON 字符串或 base64url 字符串中读取加密信封。
     */
    public Optional<ApiCryptoEnvelope> readEnvelope(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Optional.empty();
        }
        try {
            String value = normalizeEnvelopeText(rawValue);
            ApiCryptoEnvelope envelope = objectMapper.readValue(value, ApiCryptoEnvelope.class);
            return isValidEnvelope(envelope) ? Optional.of(envelope) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public JsonNode decryptRequestEnvelopeToTree(ApiCryptoEnvelope envelope) {
        try {
            return objectMapper.readTree(decryptRequestEnvelopeToJson(envelope));
        } catch (JacksonException e) {
            throw new ApiCryptoException("接口加密请求明文不是合法 JSON", e);
        }
    }

    public String decryptRequestEnvelopeToJson(ApiCryptoEnvelope envelope) {
        ApiCryptoProperties.EndpointCrypto request = properties.getRequest();
        return decryptEnvelope(envelope, request.getKey(), normalizedKeyId(request, "request"));
    }

    public String decryptResponseEnvelopeToJson(ApiCryptoEnvelope envelope) {
        ApiCryptoProperties.EndpointCrypto response = properties.getResponse();
        return decryptEnvelope(envelope, response.getKey(), normalizedKeyId(response, "response"));
    }

    /**
     * 使用请求密钥加密对象，主要用于测试和非浏览器客户端复用同一协议。
     */
    public ApiCryptoEnvelope encryptRequestValue(Object value) {
        return encryptJson(writeJson(value), properties.getRequest());
    }

    /**
     * 使用响应密钥加密 JSON 字符串。
     */
    public ApiCryptoEnvelope encryptResponseJson(String json) {
        if (!StringUtils.hasText(json)) {
            throw new ApiCryptoException("接口响应为空，无法执行响应加密");
        }
        return encryptJson(json, properties.getResponse());
    }

    public byte[] serializeEnvelope(ApiCryptoEnvelope envelope) {
        try {
            return objectMapper.writeValueAsBytes(envelope);
        } catch (JacksonException e) {
            throw new ApiCryptoException("接口加密信封序列化失败", e);
        }
    }

    /**
     * 序列化为查询参数使用的 base64url JSON 字符串。
     */
    public String serializeEnvelopeToBase64Url(ApiCryptoEnvelope envelope) {
        return encodeBase64Url(serializeEnvelope(envelope));
    }

    private ApiCryptoEnvelope encryptJson(String json, ApiCryptoProperties.EndpointCrypto endpoint) {
        ApiCryptoProperties.Strategy strategy = properties.getStrategy();
        String keyId = normalizedKeyId(endpoint, strategy == null ? "unknown" : strategy.name());
        String secret = normalizedSecret(endpoint, keyId);
        return strategy == ApiCryptoProperties.Strategy.AES_GCM_V1
                ? encryptAesGcm(json, keyId, secret)
                : encryptSm4(json, keyId, secret);
    }

    private String decryptEnvelope(ApiCryptoEnvelope envelope, String secret, String expectedKeyId) {
        assertKeyId(envelope, expectedKeyId);
        ApiCryptoProperties.Strategy strategy = ApiCryptoProperties.Strategy.fromAlgorithm(envelope.algorithm());
        String normalizedSecret = normalizeSecret(secret, "接口加密密钥未配置");
        return strategy == ApiCryptoProperties.Strategy.AES_GCM_V1
                ? decryptAesGcm(envelope, normalizedSecret)
                : decryptSm4(envelope, normalizedSecret);
    }

    private ApiCryptoEnvelope encryptAesGcm(String json, String keyId, String secret) {
        try {
            byte[] iv = randomBytes(12);
            byte[] salt = randomBytes(16);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveAesKey(secret, salt), new GCMParameterSpec(AES_GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
            return new ApiCryptoEnvelope(
                    true,
                    VERSION,
                    ApiCryptoProperties.Strategy.AES_GCM_V1.algorithm(),
                    keyId,
                    encodeBase64Url(iv),
                    encodeBase64Url(salt),
                    encodeBase64Url(encrypted),
                    null
            );
        } catch (GeneralSecurityException e) {
            throw new ApiCryptoException("AES-GCM 接口加密失败", e);
        }
    }

    private String decryptAesGcm(ApiCryptoEnvelope envelope, String secret) {
        try {
            byte[] salt = decodeBase64Url(envelope.salt());
            byte[] iv = decodeBase64Url(envelope.iv());
            byte[] encrypted = decodeBase64Url(envelope.data());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveAesKey(secret, salt), new GCMParameterSpec(AES_GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new ApiCryptoException("AES-GCM 接口解密失败", e);
        }
    }

    private ApiCryptoEnvelope encryptSm4(String json, String keyId, String secret) {
        byte[] iv = randomBytes(16);
        byte[] salt = randomBytes(16);
        String ivValue = encodeBase64Url(iv);
        String saltValue = encodeBase64Url(salt);
        String ciphertextHex = HexUtil.encodeHexStr(createSm4(secret, salt, iv).encrypt(json.getBytes(StandardCharsets.UTF_8)));
        String tagHex = calculateSm3Tag(ciphertextHex, deriveSm3MacKey(secret, salt), keyId, ivValue, saltValue);
        return new ApiCryptoEnvelope(
                true,
                VERSION,
                ApiCryptoProperties.Strategy.SM4_CBC_SM3_V1.algorithm(),
                keyId,
                ivValue,
                saltValue,
                encodeBase64Url(HexUtil.decodeHex(ciphertextHex)),
                encodeBase64Url(HexUtil.decodeHex(tagHex))
        );
    }

    private String decryptSm4(ApiCryptoEnvelope envelope, String secret) {
        if (!StringUtils.hasText(envelope.tag())) {
            throw new ApiCryptoException("SM4 接口加密信封缺少完整性校验标签");
        }
        try {
            byte[] salt = decodeBase64Url(envelope.salt());
            String ciphertextHex = HexUtil.encodeHexStr(decodeBase64Url(envelope.data()));
            String expectedTagHex = calculateSm3Tag(
                    ciphertextHex,
                    deriveSm3MacKey(secret, salt),
                    envelope.keyId(),
                    envelope.iv(),
                    envelope.salt()
            );
            byte[] expectedTag = HexUtil.decodeHex(expectedTagHex);
            byte[] actualTag = decodeBase64Url(envelope.tag());
            if (!MessageDigest.isEqual(expectedTag, actualTag)) {
                throw new ApiCryptoException("SM4 接口加密信封完整性校验失败");
            }
            byte[] plaintext = createSm4(secret, salt, decodeBase64Url(envelope.iv()))
                    .decrypt(HexUtil.decodeHex(ciphertextHex));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ApiCryptoException("SM4 接口解密失败", e);
        }
    }

    private SM4 createSm4(String secret, byte[] salt, byte[] iv) {
        byte[] key = HexUtil.decodeHex(deriveSm4Key(secret, salt));
        return new SM4(Mode.CBC, Padding.PKCS5Padding, key, iv);
    }

    private SecretKeySpec deriveAesKey(String secret, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(
                secret.toCharArray(),
                salt,
                properties.getPbkdf2Iterations(),
                256
        );
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    private String deriveSm4Key(String secret, byte[] salt) {
        return SmUtil.sm3(String.join("\n", SM4_KDF_DOMAIN, secret, HexUtil.encodeHexStr(salt))).substring(0, 32);
    }

    private String deriveSm3MacKey(String secret, byte[] salt) {
        return SmUtil.sm3(String.join("\n", SM3_MAC_DOMAIN, secret, HexUtil.encodeHexStr(salt)));
    }

    private String calculateSm3Tag(String ciphertextHex, String macKeyHex, String keyId, String iv, String salt) {
        String signText = String.join("\n", SM4_TAG_DOMAIN, keyId, iv, salt, ciphertextHex);
        return SmUtil.hmacSm3(HexUtil.decodeHex(macKeyHex)).digestHex(signText, StandardCharsets.UTF_8);
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new ApiCryptoException("接口加密明文序列化失败", e);
        }
    }

    private String normalizeEnvelopeText(String rawValue) throws JacksonException {
        String value = URLDecoder.decode(rawValue.trim(), StandardCharsets.UTF_8);
        if (value.startsWith("\"")) {
            value = objectMapper.readValue(value, String.class).trim();
        }
        if (value.startsWith("{")) {
            return value;
        }
        return new String(decodeBase64Url(value), StandardCharsets.UTF_8);
    }

    private boolean isValidEnvelope(ApiCryptoEnvelope envelope) {
        return envelope != null
                && envelope.encrypted()
                && VERSION.equals(envelope.version())
                && StringUtils.hasText(envelope.algorithm())
                && StringUtils.hasText(envelope.keyId())
                && StringUtils.hasText(envelope.iv())
                && StringUtils.hasText(envelope.salt())
                && StringUtils.hasText(envelope.data());
    }

    private void assertKeyId(ApiCryptoEnvelope envelope, String expectedKeyId) {
        if (!expectedKeyId.equals(envelope.keyId())) {
            throw new ApiCryptoException("接口加密 keyId 不匹配");
        }
    }

    private String normalizedKeyId(ApiCryptoProperties.EndpointCrypto endpoint, String direction) {
        if (endpoint == null || !StringUtils.hasText(endpoint.getKeyId())) {
            throw new ApiCryptoException("接口" + direction + "加密 keyId 未配置");
        }
        return endpoint.getKeyId().trim();
    }

    private String normalizedSecret(ApiCryptoProperties.EndpointCrypto endpoint, String keyId) {
        if (endpoint == null) {
            throw new ApiCryptoException("接口加密端点未配置，keyId=" + keyId);
        }
        return normalizeSecret(endpoint.getKey(), "接口加密密钥未配置，keyId=" + keyId);
    }

    private String normalizeSecret(String secret, String message) {
        if (!StringUtils.hasText(secret)) {
            throw new ApiCryptoException(message);
        }
        return secret.trim();
    }

    private void validateProperties(ApiCryptoProperties properties) {
        if (properties.getStrategy() == null) {
            throw new ApiCryptoException("web-plus.api-crypto.strategy 未配置");
        }
        if (properties.getPbkdf2Iterations() <= 0) {
            throw new ApiCryptoException("web-plus.api-crypto.pbkdf2-iterations 必须大于 0");
        }
        if (properties.requestEnabled()) {
            normalizedKeyId(properties.getRequest(), "请求");
            normalizedSecret(properties.getRequest(), properties.getRequest().getKeyId());
        }
        if (properties.responseEnabled()) {
            normalizedKeyId(properties.getResponse(), "响应");
            normalizedSecret(properties.getResponse(), properties.getResponse().getKeyId());
        }
    }

    private String encodeBase64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] decodeBase64Url(String value) {
        String normalized = value.trim();
        String padded = normalized + "=".repeat((4 - normalized.length() % 4) % 4);
        return Base64.getUrlDecoder().decode(padded);
    }
}
