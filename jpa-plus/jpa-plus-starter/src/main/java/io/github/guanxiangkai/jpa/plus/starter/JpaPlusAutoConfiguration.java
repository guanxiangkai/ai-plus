package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.starter.repository.JpaPlusRepositoryFactoryBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;

/**
 * JPA-Plus Spring Boot 自动装配配置（总入口 —— 薄编排层）
 *
 * <p>本类作为自动装配的统一入口，通过 {@code @Import} 引入各功能模块的独立自动装配类：</p>
 * <ul>
 *   <li>{@link JpaPlusQueryAutoConfiguration}   —— SQL 编译器、分页优化器、查询执行器</li>
 *   <li>{@link JpaPlusFieldAutoConfiguration}   —— 字段处理器（ID、填充、加密、字典等）及字段引擎</li>
 *   <li>{@link JpaPlusInterceptorAutoConfiguration} —— 自动排序、多租户等内置拦截器</li>
 *   <li>{@link JpaPlusHibernateTenantAutoConfiguration} —— Hibernate 层租户过滤与保存填充</li>
 *   <li>{@link JpaPlusAuditAutoConfiguration}       —— 审计事件发布、错误处理、AuditInterceptor</li>
 *   <li>{@link JpaPlusCoreAutoConfiguration}    —— 拦截器链、Flush 策略、统一执行器</li>
 * </ul>
 *
 * <p>本类负责 Repository Factory 替换；Micrometer 指标由独立的
 * {@link JpaPlusMetricsAutoConfiguration} 按类路径条件装配。</p>
 *
 * <p><b>配置项：</b></p>
 * <pre>{@code
 * jpa-plus:
 *   dialect: mysql
 *   debug.enabled: false
 *   debug.print-params: true
 *   debug.slow-sql.enabled: false
 *   debug.slow-sql.threshold: 1000
 *   metrics.prefix: jpa.plus
 *   flush-mode: AUTO
 *   pagination.count-strategy: SIMPLE
 *   id-generator.type: AUTO
 *   id-generator.snowflake.worker-id: 1
 *   id-generator.snowflake.datacenter-id: 1
 *   encrypt.active-version: primary
 *   encrypt.keys.primary: ${JPA_PLUS_ENCRYPT_KEY}
 *   dict.jdbc.enabled: false
 *   dict.cache.enabled: true
 *   dict.cache.ttl-seconds: 300
 *   tenant.column: tenant_id
 *   audit.async.enabled: false
 *   audit.method.enabled: true
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration(after = JpaPlusAuditAutoConfiguration.class)
@ConditionalOnProperty(name = "jpa-plus.enabled", havingValue = "true", matchIfMissing = true)
@Import({
        JpaPlusQueryAutoConfiguration.class,
        JpaPlusFieldAutoConfiguration.class,
        JpaPlusInterceptorAutoConfiguration.class,
        JpaPlusHibernateTenantAutoConfiguration.class,
        JpaPlusCoreAutoConfiguration.class
})
@Slf4j
public class JpaPlusAutoConfiguration {

    // ─────────── Repository 工厂替换 ───────────

    /**
     * 在 Repository Bean 实例化前，将 Spring Data JPA 默认的
     * {@link JpaRepositoryFactoryBean} 定义替换为
     * {@link JpaPlusRepositoryFactoryBean}，使标准 Repository 获得拦截器链、字段处理等增强能力。
     *
     * <p>替换条件严格匹配 Spring Data 默认工厂类名。使用方显式配置的自定义 Repository
     * Factory 不会被覆盖；这里也不使用普通 {@code BeanPostProcessor}，避免在 Bean 已实例化后
     * 才改变基础设施语义。</p>
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static BeanDefinitionRegistryPostProcessor jpaPlusRepositoryFactoryReplacer() {
        return (BeanDefinitionRegistry registry) -> {
            String target = JpaRepositoryFactoryBean.class.getName();
            String replacement = JpaPlusRepositoryFactoryBean.class.getName();
            for (String name : registry.getBeanDefinitionNames()) {
                var bd = registry.getBeanDefinition(name);
                if (target.equals(bd.getBeanClassName())) {
                    bd.setBeanClassName(replacement);
                }
            }
        };
    }

}
