package io.github.guanxiangkai.web.plus.log.properties;

import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * web-plus-log 配置属性
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "web-plus.log")
public record LogProperties(
        Boolean enabled,
        Boolean accessLogEnabled,
        Boolean operationLogEnabled,
        Boolean loginLogEnabled,
        Boolean ossLogEnabled,
        Boolean dataAuditEnabled,
        Boolean scheduleLogEnabled,
        Boolean aiLogEnabled,
        Boolean taskLogEnabled,
        /**
         * 是否注册 {@code OperationLogContextAccessor}。
         * <p>
         * 开启后框架会将 {@code OperationLogContextAccessor} 注册到 {@code ContextRegistry}，
         * 使 jpa-plus {@code DataAuditEvent} 监听器在 WebFlux 弹性调度线程上也能
         * 通过 {@code OperationLogContext.current()} 读取当前操作上下文，完成双表关联。
         * </p>
         * <p>Reactor 与异步任务的全局传播由 web-plus-core 负责，默认 {@code true}。</p>
         */
        Boolean contextPropagationEnabled,
        String traceHeaderName,
        List<String> ignorePaths,
        /**
         * 访问日志实体类全限定名（需继承 BaseLog）。
         * <p>配置后 {@link io.github.guanxiangkai.web.plus.log.filter.AccessLogFilter} 将自动
         * 创建该类的实例并按字段名约定填充后传入 {@link io.github.guanxiangkai.web.plus.log.spi.AccessLogHandler}。</p>
         * <p>示例：{@code web-plus.log.access-log-entity-class: com.example.SysAccessLog}</p>
         */
        String accessLogEntityClass,
        /**
         * 数据变更日志实体类全限定名（需继承 BaseLog）。
         * <p>配置后 {@link io.github.guanxiangkai.web.plus.log.bridge.JpaPlusDataAuditEventBridge} 将自动
         * 创建该类的实例并按字段名约定填充后传入 {@link io.github.guanxiangkai.web.plus.log.spi.DataChangeHandler}。</p>
         * <p>示例：{@code web-plus.log.data-change-entity-class: com.example.SysDataChangeLog}</p>
         */
        String dataChangeEntityClass
) {
    public LogProperties {
        if (enabled == null) enabled = true;
        if (accessLogEnabled == null) accessLogEnabled = true;
        if (operationLogEnabled == null) operationLogEnabled = true;
        if (loginLogEnabled == null) loginLogEnabled = true;
        if (ossLogEnabled == null) ossLogEnabled = true;
        if (dataAuditEnabled == null) dataAuditEnabled = true;
        if (scheduleLogEnabled == null) scheduleLogEnabled = true;
        if (aiLogEnabled == null) aiLogEnabled = true;
        if (taskLogEnabled == null) taskLogEnabled = true;
        if (contextPropagationEnabled == null) contextPropagationEnabled = true;
        if (traceHeaderName == null) traceHeaderName = WebPlusConstants.TRACE_ID_HEADER;
        if (ignorePaths == null) ignorePaths = List.of(
                "/actuator/**", "/favicon.ico", "/v3/api-docs/**"
        );
    }
}
