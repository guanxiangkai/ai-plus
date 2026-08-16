package io.github.guanxiangkai.web.plus.core.config;

import io.github.guanxiangkai.web.plus.core.net.TrustedProxyClientIpResolver;
import io.github.guanxiangkai.web.plus.core.properties.ClientIpProperties;
import io.github.guanxiangkai.web.plus.core.properties.TrustedForwardProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Core 模块自动配置
 * <p>
 * Spring Boot 4 自动配置
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration
@Import(JacksonConfig.class)
@EnableConfigurationProperties({ClientIpProperties.class, TrustedForwardProperties.class})
public class CoreAutoConfiguration {

    /**
     * 提供默认客户端 IP 解析策略；应用可注册同类型 Bean 覆盖。
     *
     * @param properties 显式可信代理配置
     * @param trustedForwardProperties 网关可信转发令牌配置
     * @return 客户端 IP 解析器
     */
    @Bean
    @ConditionalOnMissingBean(io.github.guanxiangkai.web.plus.core.net.ClientIpResolver.class)
    public TrustedProxyClientIpResolver clientIpResolver(
            ClientIpProperties properties,
            TrustedForwardProperties trustedForwardProperties) {
        return new TrustedProxyClientIpResolver(properties, trustedForwardProperties);
    }
}
