package io.github.guanxiangkai.web.plus.log.annotation;

import java.lang.annotation.*;

/**
 * 登录/登出日志注解
 *
 * <pre>{@code
 * @LoginLog(entity = SysLoginLog.class, action = "登录")
 * public Mono<ApiResponse<TokenVO>> login(LoginDTO dto, ServerWebExchange exchange) { ... }
 * }</pre>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.web.plus.log.spi.LoginLogHandler
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LoginLog {

    /**
     * 日志实体类（需继承 {@code BaseLog}）；切面将创建其实例并按字段名约定填充后传给 Handler
     */
    Class<?> entity() default Void.class;

    /**
     * 操作描述（如 "登录"、"登出"、"刷新Token"）
     */
    String action() default "登录";
}
