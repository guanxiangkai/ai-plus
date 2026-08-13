package io.github.guanxiangkai.web.plus.log.spi;

import io.github.guanxiangkai.web.plus.log.entity.BaseLog;

/**
 * 访问日志持久化策略 SPI
 *
 * <h3>字段名约定（实体字段名与此一致时由过滤器自动填充）</h3>
 * <pre>
 * 公共（BaseLog setter）: traceId, userId, username, tenantId, clientIp, status, logTime
 * 专属（反射写入）       : requestMethod, requestUrl, responseStatus, userAgent, costMs
 * </pre>
 *
 * <h3>推荐实现示例</h3>
 * <pre>{@code
 * // 1. 配置实体类（application.yml）
 * //    web-plus.log.access-log-entity-class: com.example.SysAccessLog
 *
 * // 2. 定义实体
 * @Entity @Table(name = "sys_access_log")
 * public class SysAccessLog extends BaseLog {
 *     private String  requestMethod;
 *     private String  requestUrl;
 *     private Integer responseStatus;
 *     private String  userAgent;
 *     private Long    costMs;
 * }
 *
 * // 3. 实现 Handler SPI
 * @Component @RequiredArgsConstructor
 * public class SysAccessLogHandler implements AccessLogHandler {
 *     private final SysAccessLogRepository repo;
 *     @Async @Override
 *     public void handle(BaseLog entity) { repo.save((SysAccessLog) entity); }
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface AccessLogHandler {
    /**
     * @param entity 已填充字段的日志实体；未配置实体类时为 {@code null}
     */
    void handle(BaseLog entity);
}
