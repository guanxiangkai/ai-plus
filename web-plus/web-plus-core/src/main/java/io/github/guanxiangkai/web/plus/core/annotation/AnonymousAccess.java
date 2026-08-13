package io.github.guanxiangkai.web.plus.core.annotation;

import java.lang.annotation.*;

/**
 * 标记匿名可访问接口，跳过认证校验。
 * <p>
 * 在 Controller 方法或类上标注此注解后，认证过滤器将放行该路径，
 * 同时 {@code web-plus-doc} 会自动移除该接口的安全声明。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AnonymousAccess {
}

