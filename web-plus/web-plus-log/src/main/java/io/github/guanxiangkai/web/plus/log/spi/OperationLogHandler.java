package io.github.guanxiangkai.web.plus.log.spi;

import io.github.guanxiangkai.web.plus.log.entity.BaseLog;

/**
 * 操作日志持久化策略 SPI
 *
 * <p>
 * 框架切面通过 {@code @OperationLog(entity = SysOperLog.class)} 指定的实体类创建实例，
 * 按字段名约定填充后传入此 Handler，业务方强转为自己的类型持久化即可。
 * </p>
 *
 * <h3>字段名约定（实体字段名与此一致时由切面自动填充）</h3>
 * <pre>
 * 公共（BaseLog setter）: traceId, userId, username, tenantId, clientIp, status, message, logTime
 * 专属（反射写入）       : operationId, module, operationTypeCode, description,
 *                         requestMethod, requestUrl, requestParams, responseData,
 *                         userAgent, costMs, errorMessage
 * </pre>
 *
 * <h3>推荐实现示例</h3>
 * <pre>{@code
 * @Entity @Table(name = "sys_oper_log")
 * public class SysOperLog extends BaseLog {
 *     private String operationId;
 *     private String module;
 *     private String operationTypeCode;
 *     private String description;
 *     private String requestMethod;
 *     private String requestUrl;
 *     @Column(columnDefinition = "TEXT") private String requestParams;
 *     @Column(columnDefinition = "TEXT") private String responseData;
 *     private String userAgent;
 *     private Long   costMs;
 *     private String errorMessage;
 * }
 *
 * @Component @RequiredArgsConstructor
 * public class SysOperLogHandler implements OperationLogHandler {
 *     private final SysOperLogRepository repo;
 *     @Async @Override
 *     public void handle(BaseLog entity) {
 *         repo.save((SysOperLog) entity);
 *     }
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface OperationLogHandler {
    /**
     * @param entity 已填充字段的日志实体（实际类型为注解 {@code entity} 指定的类）
     */
    void handle(BaseLog entity);
}
