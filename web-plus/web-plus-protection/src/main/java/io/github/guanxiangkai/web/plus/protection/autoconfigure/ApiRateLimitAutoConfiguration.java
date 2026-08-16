package io.github.guanxiangkai.web.plus.protection.autoconfigure;

import io.github.guanxiangkai.web.plus.protection.filter.ApiRateLimitFilter;
import io.github.guanxiangkai.web.plus.core.net.ClientIpResolver;
import io.github.guanxiangkai.web.plus.protection.properties.ApiRateLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 服务侧 API 限流自动配置。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties(ApiRateLimitProperties.class)
public class ApiRateLimitAutoConfiguration {

    @Bean
    @ConditionalOnClass(ReactiveStringRedisTemplate.class)
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    @ConditionalOnMissingBean(ApiRateLimitFilter.class)
    @ConditionalOnProperty(prefix = "web-plus.security.api-rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApiRateLimitFilter apiRateLimitFilter(ReactiveStringRedisTemplate redisTemplate,
                                                 ApiRateLimitProperties properties,
                                                 ObjectMapper objectMapper,
                                                 ObjectProvider<ClientIpResolver> clientIpResolver) {
        log.info("[ApiRateLimit] 服务侧 API 限流已启用: limit={}/{}s, burst={}",
                properties.limit(), properties.window().toSeconds(), properties.burst());
        return new ApiRateLimitFilter(redisTemplate, properties, objectMapper,
                clientIpResolver.getIfAvailable(ClientIpResolver::directPeer));
    }
}
