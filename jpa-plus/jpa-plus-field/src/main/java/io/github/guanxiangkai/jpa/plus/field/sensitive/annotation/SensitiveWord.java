package io.github.guanxiangkai.jpa.plus.field.sensitive.annotation;

import io.github.guanxiangkai.jpa.plus.field.sensitive.spi.SensitiveStrategy;

import java.lang.annotation.*;

/**
 * 敏感词检测注解
 *
 * <p>标注在实体字段上，保存前自动检测敏感词。
 * 根据 {@link #strategy()} 决定处理方式。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 内置策略
 * @SensitiveWord(strategy = SensitiveWordStrategy.REPLACE)
 * private String content;
 *
 * // 自定义策略
 * @SensitiveWord(customStrategy = MySensitiveStrategy.AUDIT.class)
 * private String comment;
 * }</pre>
 *
 * @author guanxiangkai
 * @see SensitiveStrategy
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SensitiveWord {

    /**
     * 内置处理策略（默认拒绝）
     */
    SensitiveWordStrategy strategy() default SensitiveWordStrategy.REJECT;

    /**
     * 自定义处理策略类（优先于 {@link #strategy()}）
     *
     * <p>指定一个实现了 {@link SensitiveStrategy} 的枚举或类。
     * 设为默认值 {@code SensitiveStrategy.class} 表示不使用自定义策略。</p>
     */
    Class<? extends SensitiveStrategy> customStrategy() default SensitiveStrategy.class;

    /**
     * 替换字符（仅 REPLACE 策略生效）
     */
    String replacement() default "***";
}
