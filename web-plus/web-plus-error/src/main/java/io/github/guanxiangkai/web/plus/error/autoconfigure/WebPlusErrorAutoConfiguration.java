package io.github.guanxiangkai.web.plus.error.autoconfigure;

import io.github.guanxiangkai.web.plus.error.handler.GlobalExceptionHandler;
import io.github.guanxiangkai.web.plus.error.properties.ErrorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * web-plus-error 自动装配
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties(ErrorProperties.class)
public class WebPlusErrorAutoConfiguration {

    public WebPlusErrorAutoConfiguration() {
        log.info("[web-plus] Error 模块已启用");
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}

