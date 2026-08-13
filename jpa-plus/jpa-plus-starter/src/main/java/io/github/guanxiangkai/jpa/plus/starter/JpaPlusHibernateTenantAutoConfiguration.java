package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.interceptor.tenant.spi.TenantIdProvider;
import io.github.guanxiangkai.jpa.plus.starter.tenant.HibernateTenantContext;
import io.github.guanxiangkai.jpa.plus.starter.tenant.JpaPlusTenantIntegrator;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.jpa.boot.spi.JpaSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JPA Plus Hibernate 层多租户自动装配。
 *
 * <p>QueryWrapper 之外的 Spring Data JPA 查询不会经过 JPA Plus 查询拦截器，
 * 因此这里在 Hibernate 元数据层为租户实体挂自动 Filter，并在保存事件中补齐租户 ID。</p>
 */
@AutoConfiguration
@ConditionalOnClass({HibernatePropertiesCustomizer.class, IntegratorProvider.class})
@ConditionalOnProperty(name = "jpa-plus.enabled", havingValue = "true", matchIfMissing = true)
public class JpaPlusHibernateTenantAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "jpaPlusHibernateTenantCustomizer")
    @ConditionalOnBean(TenantIdProvider.class)
    @ConditionalOnProperty(name = "jpa-plus.tenant.hibernate.enabled", havingValue = "true", matchIfMissing = true)
    HibernatePropertiesCustomizer jpaPlusHibernateTenantCustomizer(
            TenantIdProvider tenantIdProvider,
            @Value("${jpa-plus.tenant.property:" + HibernateTenantContext.DEFAULT_TENANT_PROPERTY + "}")
            String tenantProperty,
            @Value("${jpa-plus.tenant.column:" + HibernateTenantContext.DEFAULT_TENANT_COLUMN + "}")
            String tenantColumn,
            @Value("${jpa-plus.tenant.placeholder-values:}") String placeholderValues) {

        HibernateTenantContext tenantContext = new HibernateTenantContext(
                tenantIdProvider,
                tenantProperty,
                tenantColumn,
                parsePlaceholderValues(placeholderValues)
        );
        JpaPlusTenantIntegrator tenantIntegrator = new JpaPlusTenantIntegrator(tenantContext);

        return hibernateProperties -> {
            Object existingProvider = hibernateProperties.get(JpaSettings.INTEGRATOR_PROVIDER);
            hibernateProperties.put(HibernateTenantContext.SETTING_KEY, tenantContext);
            hibernateProperties.put(JpaSettings.INTEGRATOR_PROVIDER, mergeIntegratorProvider(
                    existingProvider,
                    tenantIntegrator
            ));
        };
    }

    private IntegratorProvider mergeIntegratorProvider(Object existingProvider, Integrator tenantIntegrator) {
        return () -> {
            List<Integrator> integrators = new ArrayList<>();
            if (existingProvider instanceof IntegratorProvider provider) {
                List<Integrator> existingIntegrators = provider.getIntegrators();
                if (existingIntegrators != null) {
                    integrators.addAll(existingIntegrators);
                }
            }
            boolean alreadyRegistered = integrators.stream()
                    .anyMatch(existing -> existing.getClass().equals(tenantIntegrator.getClass()));
            if (!alreadyRegistered) {
                integrators.add(tenantIntegrator);
            }
            return integrators;
        };
    }

    private Set<String> parsePlaceholderValues(String placeholderValues) {
        if (!StringUtils.hasText(placeholderValues)) {
            return Set.of();
        }
        return Arrays.stream(placeholderValues.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }
}
