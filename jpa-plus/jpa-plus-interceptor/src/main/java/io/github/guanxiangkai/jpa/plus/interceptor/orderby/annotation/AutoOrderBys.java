package io.github.guanxiangkai.jpa.plus.interceptor.orderby.annotation;

import java.lang.annotation.*;

/**
 * {@link AutoOrderBy} 的容器注解（可重复注解支持）
 *
 * <p>当需要在同一个实体类上声明多个排序规则时，Java 会自动将多个 {@code @AutoOrderBy}
 * 包装为一个 {@code @AutoOrderBys}。通常不需要直接使用此注解。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Entity
 * @AutoOrderBy(column = "create_time", direction = Direction.DESC, priority = 1)
 * @AutoOrderBy(column = "sort_order", direction = Direction.ASC, priority = 2)
 * public class Article {
 *     // ...
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoOrderBys {

    /**
     * 排序规则数组
     */
    AutoOrderBy[] value();
}

