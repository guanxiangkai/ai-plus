package io.github.guanxiangkai.jpa.plus.datasource.annotation;

import io.github.guanxiangkai.jpa.plus.datasource.context.JpaPlusContext;
import io.github.guanxiangkai.jpa.plus.datasource.enums.DsName;

import java.lang.annotation.*;

/**
 * 数据源切换注解
 *
 * <p>标注在方法或类上，指定执行时使用的数据源名称。
 * 配合 {@link JpaPlusContext} 实现多数据源路由。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 使用内置常量
 * @DS(DsName.SLAVE)
 * public List<User> queryFromSlave() { ... }
 *
 * // 使用 SpEL（支持方法参数、Spring Bean）
 * @DS("#{#tenant + '_ds'}")
 * public List<Order> listByTenant(String tenant) { ... }
 *
 * // 使用自定义常量
 * @DS(MyDsName.ANALYTICS_DS)
 * public Report generateReport() { ... }
 * }</pre>
 *
 * <p><b>注意：</b>在活跃事务内不允许切换数据源，防止事务一致性问题。</p>
 *
 * @author guanxiangkai
 * @see DsName
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DS {

    /**
     * 数据源名称
     *
     * <p>推荐使用 {@link DsName} 接口常量（如 {@link DsName#MASTER}、{@link DsName#SLAVE}），
     * 或用户自定义枚举中的字符串常量。</p>
     */
    String value() default DsName.MASTER;
}
