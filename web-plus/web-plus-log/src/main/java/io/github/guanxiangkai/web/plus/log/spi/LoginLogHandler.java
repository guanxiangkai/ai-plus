package io.github.guanxiangkai.web.plus.log.spi;

import io.github.guanxiangkai.web.plus.log.entity.BaseLog;

/**
 * 登录/登出日志持久化策略 SPI
 *
 * <h3>字段名约定（实体字段名与此一致时由切面自动填充）</h3>
 * <pre>
 * 公共（BaseLog setter）: traceId, userId, username, tenantId, clientIp, status, message, logTime
 * 专属（反射写入）       : action, userAgent, browser, os
 * </pre>
 *
 * <h3>推荐实现示例</h3>
 * <pre>{@code
 * @Entity @Table(name = "sys_login_log")
 * public class SysLoginLog extends BaseLog {
 *     private String action;      // 登录 / 登出
 *     private String userAgent;
 *     private String browser;
 *     private String os;
 * }
 *
 * @Component @RequiredArgsConstructor
 * public class SysLoginLogHandler implements LoginLogHandler {
 *     private final SysLoginLogRepository repo;
 *     @Async @Override
 *     public void handle(BaseLog entity) {
 *         repo.save((SysLoginLog) entity);
 *     }
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface LoginLogHandler {
    /**
     * @param entity 已填充字段的日志实体（实际类型为注解 {@code entity} 指定的类）
     */
    void handle(BaseLog entity);
}
