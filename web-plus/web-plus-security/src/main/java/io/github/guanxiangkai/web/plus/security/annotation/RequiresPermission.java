package io.github.guanxiangkai.web.plus.security.annotation;

import java.lang.annotation.*;

/**
 * 标记需要特定权限的接口
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresPermission {
    /**
     * 需要的权限代码，多个时默认需要全部满足
     */
    String[] value();

    /**
     * true = 满足任一即可，false = 必须全部满足
     */
    boolean any() default false;
}

