package io.github.guanxiangkai.web.plus.web.config;

import io.github.guanxiangkai.jpa.plus.interceptor.permission.handler.DataScopeHandler;
import io.github.guanxiangkai.web.plus.web.auditing.SpringSecurityAuditorAware;
import io.github.guanxiangkai.web.plus.web.interceptor.WebPlusDataScopeHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;

/**
 * web-plus 与 Spring Data JPA / jpa-plus 的桥接自动配置。
 */
@AutoConfiguration
public class WebPlusJpaAutoConfiguration {

    @Bean
    @ConditionalOnClass(AuditorAware.class)
    @ConditionalOnMissingBean(AuditorAware.class)
    public SpringSecurityAuditorAware springSecurityAuditorAware() {
        return new SpringSecurityAuditorAware();
    }

    @Bean
    @ConditionalOnClass(name = "io.github.guanxiangkai.jpa.plus.interceptor.permission.handler.DataScopeHandler")
    @ConditionalOnMissingBean(DataScopeHandler.class)
    public WebPlusDataScopeHandler webPlusDataScopeHandler() {
        return new WebPlusDataScopeHandler();
    }
}
