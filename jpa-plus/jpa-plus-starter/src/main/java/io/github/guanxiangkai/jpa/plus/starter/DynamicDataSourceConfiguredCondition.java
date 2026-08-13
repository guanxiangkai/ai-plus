package io.github.guanxiangkai.jpa.plus.starter;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;


/**
 * 自定义条件：检测 {@code spring.datasource.dynamic.datasource} 下是否至少配置了一个数据源
 *
 * <p>{@code @ConditionalOnProperty} 无法检测 Map 类型属性，
 * 因此需要使用 {@link Binder} 来判断是否存在配置。</p>
 *
 * <p>仅当配置包含至少一个动态数据源时匹配，避免空 Map 触发自动配置。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
class DynamicDataSourceConfiguredCondition implements Condition {

    private static final String DATASOURCE_PREFIX = "spring.datasource.dynamic.datasource";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return Binder.get(context.getEnvironment())
                .bind(DATASOURCE_PREFIX, Bindable.mapOf(String.class, Object.class))
                .map(map -> !map.isEmpty())
                .orElse(false);
    }
}

