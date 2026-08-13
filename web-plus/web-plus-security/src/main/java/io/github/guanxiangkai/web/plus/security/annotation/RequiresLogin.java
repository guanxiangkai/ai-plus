package io.github.guanxiangkai.web.plus.security.annotation;

import java.lang.annotation.*;

/**
 * 标记需要登录才可访问的接口
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresLogin {
}

