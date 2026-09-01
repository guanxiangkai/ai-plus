package io.github.guanxiangkai.web.plus.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口启用 API 入参解密和出参加密。
 *
 * <p>默认同时启用入参解密和出参加密。只有业务 Controller 或其方法显式标注本注解时，
 * 对应端点才会参与处理；路径名称和基类不会隐式启用或绕过加密。启用请求解密或响应加密的
 * 方向必须使用非流式 JSON 契约；非 JSON、SSE、NDJSON 和文件端点应显式关闭对应方向。</p>
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
