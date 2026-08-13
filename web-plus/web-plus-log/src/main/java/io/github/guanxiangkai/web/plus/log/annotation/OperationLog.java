package io.github.guanxiangkai.web.plus.log.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解 —— 标注在 Controller 方法上，自动记录操作日志
 *
 * <pre>{@code
 * @OperationLog(entity = SysOperLog.class, module = "用户管理", typeCode = "INSERT")
 * public Mono<ApiResponse<String>> createUser(@RequestBody CreateUserDTO dto) { ... }
 * }</pre>
 *
 * <p>
 * {@code module}、{@code typeCode}、{@code description} 均支持 SpEL 模板（{@code #{expr}}）。
 * 切面通过 {@code entity} 指定的实体类（需继承 {@link io.github.guanxiangkai.web.plus.log.entity.BaseLog}）
 * 实例化日志对象，按字段名约定填充后传入 {@link io.github.guanxiangkai.web.plus.log.spi.OperationLogHandler}。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OperationLog {

    /**
     * 日志实体类（需继承 {@code BaseLog}）；切面将创建其实例并按字段名约定填充后传给 Handler
     */
    Class<?> entity() default Void.class;

    /**
     * 所属模块，支持 SpEL 模板
     */
    String module() default "";

    /** 操作类型编码，支持 SpEL 模板 */
    String typeCode() default "";

    /** 操作描述，支持 SpEL 模板 */
    String description() default "";

    /** 是否保存请求参数（默认 true） */
    boolean saveRequestParams() default true;

    /** 是否保存响应结果（默认 false） */
    boolean saveResponseData() default false;
}
