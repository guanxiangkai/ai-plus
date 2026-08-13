package io.github.guanxiangkai.web.plus.log.annotation;

import java.lang.annotation.*;

/**
 * OSS 上传日志上下文注解
 *
 * <p>
 * 标注在文件上传入口方法上，为当前调用链生成 {@code operationId} 并写入
 * {@link io.github.guanxiangkai.web.plus.log.context.OperationLogContext}，供
 * {@code FileService.upload(...)} 在后续异步上传过程中关联
 * {@link io.github.guanxiangkai.web.plus.log.spi.OssLogHandler}。
 * </p>
 *
 * <pre>{@code
 * @OssLog(description = "上传合同附件")
 * public Mono<ApiResponse<FileUploadResult>> upload(@RequestPart("file") FilePart file) { ... }
 * }</pre>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.web.plus.log.aspect.OssLogAspect
 * @see io.github.guanxiangkai.web.plus.log.spi.OssLogHandler
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OssLog {

    /**
     * 模块名，写入操作上下文，默认 {@code OSS}
     */
    String module() default "OSS";

    /**
     * 操作类型编码，默认 {@code OSS_UPLOAD}
     */
    String typeCode() default "OSS_UPLOAD";

    /**
     * 操作描述，默认 {@code 文件上传}
     */
    String description() default "文件上传";
}

