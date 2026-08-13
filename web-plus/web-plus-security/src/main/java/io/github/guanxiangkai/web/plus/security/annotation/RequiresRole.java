package io.github.guanxiangkai.web.plus.security.annotation;

import java.lang.annotation.*;

/**
 * 标记需要特定角色的接口
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresRole {
    String[] value();

    boolean any() default false;
}

