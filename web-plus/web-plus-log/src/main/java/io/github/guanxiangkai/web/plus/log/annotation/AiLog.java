package io.github.guanxiangkai.web.plus.log.annotation;

import java.lang.annotation.*;

/**
 * AI 模型调用日志注解
 *
 * <pre>{@code
 * @AiLog(entity = SysAiCallLog.class, provider = "openai", model = "gpt-4o", description = "用户意图分析")
 * public Mono<String> analyzeIntent(String prompt) { ... }
 * }</pre>
 *
 * <p>Token 统计由业务方在 {@link io.github.guanxiangkai.web.plus.log.spi.AiCallLogHandler} 中填充（
 * 框架层无法通用解析 AI SDK 的 token 统计）。</p>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.web.plus.log.spi.AiCallLogHandler
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AiLog {

    /**
     * 日志实体类（需继承 {@code BaseLog}）；切面将创建其实例并按字段名约定填充后传给 Handler
     */
    Class<?> entity() default Void.class;

    /**
     * AI 服务提供商（如 openai、qianwen、zhipu），支持 SpEL 模板
     */
    String provider() default "";

    /** 模型名称（如 gpt-4o、qwen-turbo），支持 SpEL 模板 */
    String model() default "";

    /** 操作描述，支持 SpEL 模板 */
    String description() default "";

    /**
     * 是否保存输入内容（默认 false）。
     *
     * <p>输入可能包含 Prompt、个人信息或业务正文；只有完成数据分级、授权与脱敏后，
     * 才能在具体方法上显式开启。</p>
     */
    boolean saveInputContent() default false;

    /**
     * 是否保存输出内容（默认 false）。
     *
     * <p>输出可能包含模型复述的敏感内容；只有完成数据分级、授权与脱敏后，
     * 才能在具体方法上显式开启。</p>
     */
    boolean saveOutputContent() default false;
}
