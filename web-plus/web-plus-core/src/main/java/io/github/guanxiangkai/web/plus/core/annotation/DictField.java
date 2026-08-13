package io.github.guanxiangkai.web.plus.core.annotation;

import java.lang.annotation.*;

/**
 * 字典字段标注
 * <p>
 * 标注在 VO / DTO 的字段上，声明该字段的值需从字典缓存（Redis）中翻译为可读标签。
 * 业务侧在需要回写字典标签时，调用
 * {@code DictTranslator.translate(obj)} 完成翻译。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class UserVO extends BaseVO {
 *
 *     /** 状态存储值（如 "1"） *{@literal /}
 *     @DictField(type = "sys_status")
 *     private String status;
 *
 *     /** 自动回写的标签字段（"statusLabel"） *{@literal /}
 *     private String statusLabel;
 *
 *     /** 显式指定目标字段名 *{@literal /}
 *     @DictField(type = "sys_gender", labelField = "genderText")
 *     private String gender;
 *
 *     private String genderText;
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DictField {

    /**
     * 字典类型标识，对应 Redis 中的字典分组键（如 {@code "sys_status"}）。
     */
    String type();

    /**
     * 目标标签字段名。
     * <p>留空时默认为被标注字段名 + {@code "Label"}，例如 {@code status} → {@code statusLabel}。</p>
     */
    String labelField() default "";
}

