package io.github.guanxiangkai.web.plus.security.annotation;

import java.lang.annotation.*;

/**
 * 标记需要拥有任意一个指定角色才可访问的接口
 * <p>
 * 与 {@link RequiresRole} 的区别：{@link RequiresRole} 需要同时拥有所有指定角色（AND），
 * 本注解只需拥有其中任意一个（OR）。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresAnyRole {

    /**
     * 角色列表，当前用户拥有其中任意一个角色即可访问
     */
    String[] value();

    /**
     * 未满足时的提示信息
     */
    String message() default "无访问权限";
}

