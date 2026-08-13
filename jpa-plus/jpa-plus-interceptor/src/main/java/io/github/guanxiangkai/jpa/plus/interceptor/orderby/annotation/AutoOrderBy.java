package io.github.guanxiangkai.jpa.plus.interceptor.orderby.annotation;

import io.github.guanxiangkai.jpa.plus.interceptor.orderby.enums.Direction;

import java.lang.annotation.*;

/**
 * 自动排序注解
 *
 * <p>标注在实体字段或实体类上，声明默认排序规则。
 * 当查询未显式指定 ORDER BY 时，框架自动按该注解配置的排序规则排列结果集。</p>
 *
 * <p>支持多字段排序，通过 {@link #priority()} 控制排序字段的先后顺序。</p>
 *
 * <h3>用法一：标注在字段上（列名自动推导）</h3>
 * <pre>{@code
 * @Entity
 * public class Article {
 *
 *     @AutoOrderBy(direction = Direction.DESC, priority = 1)
 *     private LocalDateTime createTime;
 *
 *     @AutoOrderBy(direction = Direction.ASC, priority = 2)
 *     private Integer sortOrder;
 * }
 * }</pre>
 *
 * <h3>用法二：标注在类上（可重复，必须指定 column）</h3>
 * <pre>{@code
 * @Entity
 * @AutoOrderBy(column = "create_time", direction = Direction.DESC, priority = 1)
 * @AutoOrderBy(column = "sort_order", direction = Direction.ASC, priority = 2)
 * public class Article {
 *     // ...
 * }
 * }</pre>
 *
 * <p>效果：查询 Article 时若未手动 orderBy，自动追加
 * {@code ORDER BY create_time DESC, sort_order ASC}</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(AutoOrderBys.class)
public @interface AutoOrderBy {

    /**
     * 排序方向（默认升序）
     */
    Direction direction() default Direction.ASC;

    /**
     * 排序优先级（值越小越靠前，默认 0）
     *
     * <p>当实体上有多个 {@code @AutoOrderBy} 字段时，
     * 按 priority 从小到大排列，作为 ORDER BY 子句中的先后顺序。</p>
     */
    int priority() default 0;

    /**
     * 自定义列名（默认空，使用驼峰转蛇形命名）
     *
     * <p>若字段对应的数据库列名与驼峰转换结果不一致，可手动指定。</p>
     */
    String column() default "";
}
