package io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.annotation;

import io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.enums.LogicDeleteValue;

import java.lang.annotation.*;

/**
 * 逻辑删除注解
 *
 * <p>标注在逻辑删除标识字段上，框架根据<b>字段类型</b>自动推导删除值/未删除值：
 * <ul>
 *   <li>{@code Integer / int} → 已删除=1, 未删除=0</li>
 *   <li>{@code Boolean / boolean} → 已删除=true, 未删除=false</li>
 *   <li>{@code Long / long} → 已删除=1L, 未删除=0L</li>
 *   <li>{@code String} → ���删除={@link #value()}, 未删除={@link #defaultValue()}</li>
 * </ul>
 * </p>
 *
 * <p>对于 String 类型字段，可通过 {@link #value()}/{@link #defaultValue()} 自定义值（如 "Y"/"N"）。
 * 其他类型无需任何配置，框架自动处理。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // Integer → 自动推导 0/1
 * @LogicDelete
 * private Integer deleted;
 *
 * // Boolean → 自动推导 false/true
 * @LogicDelete
 * private Boolean deleted;
 *
 * // String → 自定义 "N"/"Y"
 * @LogicDelete(value = "Y", defaultValue = "N")
 * private String deleted;
 *
 * // 高级：完全自定义策略
 * @LogicDelete(customType = MyDeleteType.TIMESTAMP.class)
 * private Long deletedAt;
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogicDelete {

    /**
     * 已删除值（仅 String 类型字段需要，其他类型自动推导）
     */
    String value() default "1";

    /**
     * 未删除值（仅 String 类型字段需要，其他类型自动推导���
     */
    String defaultValue() default "0";

    /**
     * 自定义逻辑删除值策略类（优先于字段类型推导）
     *
     * <p>指定一个实现了 {@link LogicDeleteValue} 的枚举或类。
     * 设为默认值 {@code LogicDeleteValue.class} 表示使用字段类型自动推导。</p>
     */
    Class<? extends LogicDeleteValue> customType() default LogicDeleteValue.class;
}
