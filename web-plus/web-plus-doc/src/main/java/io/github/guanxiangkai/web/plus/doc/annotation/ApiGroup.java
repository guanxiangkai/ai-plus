package io.github.guanxiangkai.web.plus.doc.annotation;

import java.lang.annotation.*;

/**
 * API 分组注解 —— 标记在 Controller 类或方法上，配合 SpringDoc 进行 API 分组
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiGroup {
    /**
     * 分组名称
     */
    String value();

    /**
     * 分组描述
     */
    String description() default "";
}

