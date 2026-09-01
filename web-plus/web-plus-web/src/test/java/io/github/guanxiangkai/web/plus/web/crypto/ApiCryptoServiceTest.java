package io.github.guanxiangkai.web.plus.web.crypto;

import io.github.guanxiangkai.web.plus.web.properties.ApiCryptoProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiCryptoServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(ApiCryptoProperties.Strategy.class)
    void shouldEncryptAndDecryptRequestEnvelope(ApiCryptoProperties.Strategy strategy) {
        ApiCryptoService service = createService(strategy, true, true);
        Map<String, Object> plain = Map.of(
                "keyword", "应收账款",
                "page", 2,
                "tags", List.of("overdue", "vip")
        );

        ApiCryptoEnvelope envelope = service.encryptRequestValue(plain);
        JsonNode decrypted = service.decryptRequestEnvelopeToTree(envelope);

        assertThat(envelope.version()).isEqualTo("2");
        assertThat(envelope.algorithm()).isEqualTo(strategy.algorithm());
        assertThat(envelope.keyId()).isEqualTo("request-default");
        assertThat(decrypted.get("keyword").asString()).isEqualTo("应收账款");
        assertThat(decrypted.get("page").asInt()).isEqualTo(2);
        assertThat(decrypted.get("tags").get(0).asString()).isEqualTo("overdue");
    }

    @Test
    void shouldReadEnvelopeFromJsonAndBase64UrlText() throws Exception {
        ApiCryptoService service = createService(ApiCryptoProperties.Strategy.SM4_CBC_SM3_V1, true, false);
        ApiCryptoEnvelope envelope = service.encryptRequestValue(Map.of("name", "张三"));
        String json = new String(service.serializeEnvelope(envelope), StandardCharsets.UTF_8);
        String queryValue = service.serializeEnvelopeToBase64Url(envelope);

        assertThat(service.readEnvelope(json)).contains(envelope);
        assertThat(service.readEnvelope(queryValue)).contains(envelope);
        assertThat(service.readEnvelope(objectMapper.writeValueAsString(json))).contains(envelope);
    }

    @Test
    void shouldRejectUnsupportedEnvelopeVersion() {
        ApiCryptoService service = createService(ApiCryptoProperties.Strategy.AES_GCM_V1, true, false);
        ApiCryptoEnvelope current = service.encryptRequestValue(Map.of("name", "张三"));
        ApiCryptoEnvelope unsupportedVersion = new ApiCryptoEnvelope(
                current.encrypted(),
                "1",
                current.algorithm(),
                current.keyId(),
                current.iv(),
                current.salt(),
                current.data(),
                current.tag()
        );

        assertThat(service.readEnvelope(new String(service.serializeEnvelope(unsupportedVersion), StandardCharsets.UTF_8)))
                .isEmpty();
    }

    @Test
    void shouldReadAesEnvelopeWithoutOptionalTagField() {
        ApiCryptoService service = createService(ApiCryptoProperties.Strategy.AES_GCM_V1, true, false);
        ApiCryptoEnvelope envelope = service.encryptRequestValue(Map.of("name", "张三"));
        String jsonWithoutTag = new String(service.serializeEnvelope(envelope), StandardCharsets.UTF_8)
                .replace(",\"tag\":null", "");

        ApiCryptoEnvelope parsed = service.readEnvelope(jsonWithoutTag).orElseThrow();

        assertThat(parsed.tag()).isNull();
        assertThat(service.decryptRequestEnvelopeToTree(parsed).get("name").asString()).isEqualTo("张三");
    }

    @Test
    void shouldEncryptAndDecryptResponseWithSeparateKey() throws Exception {
        ApiCryptoService service = createService(ApiCryptoProperties.Strategy.SM4_CBC_SM3_V1, false, true);

        ApiCryptoEnvelope envelope = service.encryptResponseJson("""
                {"code":200,"data":{"name":"张三"}}
                """);
        JsonNode decrypted = objectMapper.readTree(service.decryptResponseEnvelopeToJson(envelope));

        assertThat(envelope.keyId()).isEqualTo("response-default");
        assertThat(decrypted.get("code").asInt()).isEqualTo(200);
        assertThat(decrypted.get("data").get("name").asString()).isEqualTo("张三");
    }

    @Test
    void shouldRejectMismatchedKeyId() {
        ApiCryptoService service = createService(ApiCryptoProperties.Strategy.AES_GCM_V1, true, false);
        ApiCryptoEnvelope envelope = service.encryptRequestValue(Map.of("name", "张三"));
        ApiCryptoEnvelope mismatched = new ApiCryptoEnvelope(
                envelope.encrypted(),
                envelope.version(),
                envelope.algorithm(),
                "another-key",
                envelope.iv(),
                envelope.salt(),
                envelope.data(),
                envelope.tag()
        );

        assertThatThrownBy(() -> service.decryptRequestEnvelopeToJson(mismatched))
                .isInstanceOf(ApiCryptoException.class)
                .hasMessageContaining("keyId 不匹配");
    }

    @Test
    void shouldRejectEnvelopeAlgorithmOutsideConfiguredPolicy() {
        ApiCryptoService aesService = createService(ApiCryptoProperties.Strategy.AES_GCM_V1, true, false);
        ApiCryptoService sm4Service = createService(ApiCryptoProperties.Strategy.SM4_CBC_SM3_V1, true, false);
        ApiCryptoEnvelope aesEnvelope = aesService.encryptRequestValue(Map.of("name", "张三"));

        assertThatThrownBy(() -> sm4Service.decryptRequestEnvelopeToJson(aesEnvelope))
                .isInstanceOf(ApiCryptoException.class)
                .hasMessageContaining("接口加密策略不匹配")
                .hasMessageContaining("SM4_CBC_SM3_V1")
                .hasMessageContaining("AES_GCM_V1");
    }

    @Test
    void shouldRejectUnsupportedEnvelopeAlgorithmAsCryptoError() {
        ApiCryptoService service = createService(ApiCryptoProperties.Strategy.SM4_CBC_SM3_V1, true, false);
        ApiCryptoEnvelope current = service.encryptRequestValue(Map.of("name", "张三"));
        ApiCryptoEnvelope unsupported = new ApiCryptoEnvelope(
                current.encrypted(),
                current.version(),
                "UNKNOWN",
                current.keyId(),
                current.iv(),
                current.salt(),
                current.data(),
                current.tag());

        assertThatThrownBy(() -> service.decryptRequestEnvelopeToJson(unsupported))
                .isInstanceOf(ApiCryptoException.class)
                .hasMessageContaining("不支持的算法");
    }

    @Test
    void shouldRequireKeysOnlyWhenDirectionIsEnabled() {
        ApiCryptoProperties properties = new ApiCryptoProperties();
        properties.setEnabled(true);
        properties.getRequest().setEnabled(false);
        properties.getResponse().setEnabled(false);

        ApiCryptoService service = new ApiCryptoService(properties, objectMapper);

        assertThat(service.requestEnabled()).isFalse();
        assertThat(service.responseEnabled()).isFalse();
    }

    private ApiCryptoService createService(ApiCryptoProperties.Strategy strategy, boolean requestEnabled, boolean responseEnabled) {
        ApiCryptoProperties properties = new ApiCryptoProperties();
        properties.setEnabled(true);
        properties.setStrategy(strategy);
        properties.getRequest().setEnabled(requestEnabled);
        properties.getRequest().setKey("reference-request-secret");
        properties.getResponse().setEnabled(responseEnabled);
        properties.getResponse().setKey("reference-response-secret");
        return new ApiCryptoService(properties, objectMapper);
    }
}
