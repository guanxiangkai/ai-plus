package io.github.guanxiangkai.jpa.plus.sharding.spi;

import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.sharding.annotation.Sharding;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * 分片键提取器（SPI）
 *
 * <p>从实体对象中提取分片键值，供 {@link io.github.guanxiangkai.jpa.plus.sharding.spi.ShardingAlgorithm}
 * 计算路由目标使用。</p>
 *
 * <h3>框架默认行为</h3>
 * <p>若用户未注册自定义实现，框架使用内置的
 * {@link AnnotationShardingKeyExtractor}：优先解析实体中标注了 {@link Sharding}
 * 的字段；若未标注则回退到 {@link io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRule#shardingKeyField()} 配置的字段名。</p>
 *
 * <h3>自定义示例</h3>
 * <pre>{@code
 * // 注册为 Spring Bean
 * public class TenantShardingKeyExtractor implements ShardingKeyExtractor {
 *
 *     public Object extract(Object entity, ShardingRule rule) {
 *         if (entity instanceof TenantEntity te) {
 *             return te.getTenantId();
 *         }
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * <p><b>设计模式：</b>策略模式（Strategy）</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface ShardingKeyExtractor {

    /**
     * 从实体对象中提取分片键值
     *
     * @param entity 实体对象（不为 {@code null}）
     * @param rule   该实体对应的分片规则
     * @return 分片键值，为 {@code null} 时路由器将抛出 {@link IllegalArgumentException}
     */
    Object extract(Object entity, io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRule rule);

    // ─────────────────────────────────────────────
    // 内置默认实现
    // ─────────────────────────────────────────────

    /**
     * 基于 {@link Sharding} 注解的默认提取器
     *
     * <p>优先查找 {@code @Sharding} 注解字段，回退到 {@code rule.shardingKeyField()} 字段名。</p>
     */
    class AnnotationShardingKeyExtractor implements ShardingKeyExtractor {

        private static final ClassValue<Optional<Field>> ANNOTATED_FIELD = new ClassValue<>() {
            @Override
            protected Optional<Field> computeValue(Class<?> type) {
                return ReflectionUtils.getHierarchyFields(type).stream()
                        .filter(field -> field.isAnnotationPresent(Sharding.class))
                        .findFirst();
            }
        };

        @Override
        public Object extract(Object entity, io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRule rule) {
            Class<?> clazz = entity.getClass();

            // ① 优先找 @Sharding 注解字段
            Field shardingField = ANNOTATED_FIELD.get(clazz).orElse(null);

            // ② 回退到规则中配置的字段名
            if (shardingField == null && rule.shardingKeyField() != null) {
                shardingField = ReflectionUtils.findField(clazz, rule.shardingKeyField());
            }

            if (shardingField == null) {
                throw new IllegalStateException(
                        "No sharding key field found for entity [" + clazz.getName() +
                                "]. Please annotate a field with @Sharding or configure 'shardingKeyField' in ShardingRule.");
            }

            try {
                return shardingField.get(entity);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot access sharding key field: " + shardingField.getName(), e);
            }
        }
    }
}
