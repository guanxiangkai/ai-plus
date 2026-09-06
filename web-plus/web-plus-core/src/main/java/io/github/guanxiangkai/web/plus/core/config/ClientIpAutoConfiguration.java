package io.github.guanxiangkai.web.plus.core.config;

import io.github.guanxiangkai.web.plus.core.net.ClientIpResolver;
import io.github.guanxiangkai.web.plus.core.net.TrustedProxyClientIpResolver;
import io.github.guanxiangkai.web.plus.core.properties.ClientIpProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 客户端 IP 解析器自动配置。
 *
 * <p>默认没有可信代理，因此只返回 TCP 连接对端地址。业务方可注册
 * {@link ClientIpResolver} Bean 覆盖该策略。</p>
 *
 * @author guanxiangkai
 */
@AutoConfiguration
@EnableConfigurationProperties(ClientIpProperties.class)
public class ClientIpAutoConfiguration {

    /** 创建默认的显式可信代理解析器。 */
    @Bean
    @ConditionalOnMissingBean(ClientIpResolver.class)
    public ClientIpResolver clientIpResolver(ClientIpProperties properties) {
        return new TrustedProxyClientIpResolver(properties.trustedProxies());
    }
}
