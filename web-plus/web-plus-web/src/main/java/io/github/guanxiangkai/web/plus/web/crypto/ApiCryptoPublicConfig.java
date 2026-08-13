package io.github.guanxiangkai.web.plus.web.crypto;

import io.github.guanxiangkai.web.plus.web.properties.ApiCryptoProperties;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.util.List;

/**
 * API 加密公开配置。
 *
 * <p>该模型只包含前端可公开读取的协议配置，不包含请求密钥和响应密钥。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@RegisterReflectionForBinding
public record ApiCryptoPublicConfig(
        boolean enabled,
        String strategy,
        int pbkdf2Iterations,
        Direction request,
        Direction response,
        List<ApiCryptoEndpointRule> rules,
        Headers headers,
        String queryParam,
        String configPath
) {

    public static final String CONFIG_PATH = "/web-plus/api-crypto/config";

    public static ApiCryptoPublicConfig from(ApiCryptoProperties properties, List<ApiCryptoEndpointRule> rules) {
        ApiCryptoProperties.Strategy strategy = properties.getStrategy() == null
                ? ApiCryptoProperties.Strategy.SM4_CBC_SM3_V1
                : properties.getStrategy();
        return new ApiCryptoPublicConfig(
                properties.isEnabled(),
                strategy.name(),
                properties.getPbkdf2Iterations(),
                Direction.from(properties.getRequest()),
                Direction.from(properties.getResponse()),
                List.copyOf(rules),
                new Headers(ApiCryptoService.CRYPTO_HEADER, ApiCryptoService.CRYPTO_KEY_ID_HEADER),
                ApiCryptoService.CRYPTO_QUERY_PARAM,
                CONFIG_PATH
        );
    }

    /**
     * 单向加密公开配置。
     */
    @RegisterReflectionForBinding
    public record Direction(
            boolean enabled,
            String keyId
    ) {

        static Direction from(ApiCryptoProperties.EndpointCrypto endpoint) {
            if (endpoint == null) {
                return new Direction(false, "");
            }
            return new Direction(endpoint.isEnabled(), endpoint.getKeyId());
        }
    }

    /**
     * 加密协议请求头名称。
     */
    @RegisterReflectionForBinding
    public record Headers(
            String crypto,
            String keyId
    ) {
    }
}
