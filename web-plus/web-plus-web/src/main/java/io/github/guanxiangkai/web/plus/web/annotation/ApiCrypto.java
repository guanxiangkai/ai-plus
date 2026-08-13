package io.github.guanxiangkai.web.plus.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口启用 API 入参解密和出参加密。
 *
 * <p>默认同时启用入参解密和出参加密；未标注该注解的接口保持普通明文接口行为。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiCrypto {

    /**
     * 是否对请求入参执行解密。
     */
    boolean request() default true;

    /**
     * 是否对响应出参执行加密。
     */
    boolean response() default true;
}
