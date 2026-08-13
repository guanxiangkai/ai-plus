package io.github.guanxiangkai.web.plus.web.log.entity;

import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;

/**
 * 操作日志传输实体
 * <p>
 * 作为各业务服务 → MQ → System 服务的操作日志消息载体。
 * 由 {@code @OperationLog} / {@code OperationLogAspect} 创建并填充后发布到
 * topic {@code log.operation}，System 服务消费后映射到
 * 业务侧 {@code OperationLog} JPA 实体落库。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysOperationLog extends BaseLog {

    @Serial
    private static final long serialVersionUID = 1L;

    private String operationId;
    private String module;
    private String operationType;
    private String description;
    private String method;
    private String requestUrl;
    private String requestParam;
    private String responseData;
    private String userAgent;
    private Long executionTime;
}
