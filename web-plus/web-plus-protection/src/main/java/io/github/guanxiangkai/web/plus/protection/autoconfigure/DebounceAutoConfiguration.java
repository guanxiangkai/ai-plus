package io.github.guanxiangkai.web.plus.protection.autoconfigure;

import io.github.guanxiangkai.web.plus.protection.filter.DebounceFilter;
import io.github.guanxiangkai.web.plus.protection.properties.DebounceProperties;
import lombok.extern.slf4j.Slf4j;
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
 * API 防抖自动配置。
 */
@Slf4j
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties(DebounceProperties.class)
public class DebounceAutoConfiguration {

    @Bean
    @ConditionalOnClass(ReactiveStringRedisTemplate.class)
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    @ConditionalOnMissingBean(DebounceFilter.class)
    @ConditionalOnProperty(prefix = "web-plus.debounce", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DebounceFilter debounceFilter(ReactiveStringRedisTemplate redisTemplate,
                                         DebounceProperties debounceProperties,
                                         ObjectMapper objectMapper) {
        log.info("[web-plus] API 防抖过滤器已启用（窗口={}）", debounceProperties.duration());
        return new DebounceFilter(redisTemplate, debounceProperties, objectMapper);
    }
}
