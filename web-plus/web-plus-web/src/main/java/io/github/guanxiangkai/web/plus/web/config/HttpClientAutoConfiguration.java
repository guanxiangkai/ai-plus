package io.github.guanxiangkai.web.plus.web.config;

import io.github.guanxiangkai.web.plus.web.client.ExternalHttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * HTTP 客户端自动配置
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(WebClient.class)
public class HttpClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(WebClient.Builder.class)
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExternalHttpClient externalHttpClient(WebClient.Builder webClientBuilder) {
        return new ExternalHttpClient(webClientBuilder);
    }
}
