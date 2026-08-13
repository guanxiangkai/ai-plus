package io.github.guanxiangkai.jpa.plus.field.desensitize.annotation;

import io.github.guanxiangkai.jpa.plus.field.desensitize.spi.MaskStrategy;

import java.lang.annotation.*;

/**
 * 字段脱敏注解
 *
 * <p>标注在实体字段上，查询后自动对敏感数据进行掩码处理。
 * 仅支持 {@link String} 类型字段。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 使用内置策略
 * @Desensitize(strategy = DesensitizeStrategy.PHONE)
 * private String phone;
 *
 * // 使用自定义策略
 * @Desensitize(customStrategy = MyMaskStrategy.IP_ADDRESS.class)
 * private String ipAddress;
 * }</pre>
 *
 * @author guanxiangkai
 * @see MaskStrategy
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Desensitize {

    /**
     * 内置脱敏策略（默认 CUSTOM）
     */
    DesensitizeStrategy strategy() default DesensitizeStrategy.CUSTOM;

    /**
     * 自定义脱敏策略类（优先于 {@link #strategy()}）
     *
     * <p>指定一个实现了 {@link MaskStrategy} 的枚举或类，
     * 框架会通过反射实例化（枚举取第一个常量，类调无参构造）。
     * 设为默认值 {@code MaskStrategy.class} 表示不使用自定义策略。</p>
     */
    Class<? extends MaskStrategy> customStrategy() default MaskStrategy.class;

    /**
     * 掩码字符（默认 *）
     */
    char maskChar() default '*';
}
