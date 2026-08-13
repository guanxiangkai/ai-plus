package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.datasource.spi.DataSourcePostProcessor;
import io.github.guanxiangkai.jpa.plus.starter.DynamicDataSourceProperties.DatasourceProxyProperties;
import lombok.extern.slf4j.Slf4j;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

/**
 * datasource-proxy 数据源后置处理器
 *
 * <p>通过 {@link DataSourcePostProcessor} 扩展点，将每一个动态数据源包装为
 * {@code datasource-proxy} 代理，实现 JDBC 层 SQL 全量拦截、格式化输出和慢查询告警。</p>
 *
 * <h3>与 jpa-plus.debug.enabled 的区别</h3>
 * <table border="1">
 *   <tr><th>特性</th><th>jpa-plus.debug.enabled</th><th>datasource-proxy</th></tr>
 *   <tr><td>拦截范围</td><td>仅 jpa-plus 编译的查询 SQL</td><td>所有 JDBC SQL（含 Hibernate / Spring Data）</td></tr>
 *   <tr><td>参数绑定</td><td>打印命名参数 Map</td><td>打印完整带值 SQL（可直接复制执行）</td></tr>
 *   <tr><td>执行时间</td><td>不记录</td><td>记录每条 SQL 的执行耗时</td></tr>
 *   <tr><td>慢查询告警</td><td>WARN 日志（jpa-plus SQL）</td><td>WARN 日志（全部 JDBC SQL）</td></tr>
 *   <tr><td>工作层</td><td>编译器装饰器</td><td>DataSource 代理（JDBC Connection 级别）</td></tr>
 * </table>
 *
 * <h3>激活方式</h3>
 * <pre>{@code
 * # application.yml
 * spring:
 *   datasource:
 *     dynamic:
 *       datasource-proxy:
 *         enabled: true
 *         log-level: debug            # 常规 SQL 输出日志级别（trace/debug/info/warn，默认 debug）
 *         multiline: false            # 是否格式化 SQL（多行缩进输出，便于阅读）
 *         slow-query-threshold: 2000  # 慢查询阈值（毫秒），超过此值输出 warn，0 = 禁用
 * }</pre>
 * <p>同时需要在 classpath 中引入 {@code datasource-proxy} 依赖：</p>
 * <pre>{@code
 * // Gradle
 * runtimeOnly("net.ttddyy:datasource-proxy:1.10")
 * }</pre>
 *
 * <h3>顺序说明</h3>
 * <p>使用 {@link Ordered#LOWEST_PRECEDENCE} 确保 datasource-proxy 是最外层的 DataSource 包装器，
 * 捕获所有应用层 SQL（包括经其他 PostProcessor 处理后的 DataSource 发出的 SQL）。</p>
 *
 * <p><b>设计模式：</b>装饰器模式（Decorator） —— 透明增强 DataSource，应用层无感知</p>
 *
 * @author guanxiangkai
 * @see DataSourcePostProcessor
 * @see DatasourceProxyProperties
 * @since 1.0.0
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
class DatasourceProxyDataSourcePostProcessor implements DataSourcePostProcessor {

    private final DatasourceProxyProperties properties;

    DatasourceProxyDataSourcePostProcessor(DatasourceProxyProperties properties) {
        this.properties = properties;
        log.info("[jpa-plus] datasource-proxy SQL interceptor enabled — " +
                        "log-level={}, multiline={}, slow-query-threshold={}ms",
                properties.getLogLevel(),
                properties.isMultiline(),
                properties.getSlowQueryThreshold());
    }

    private static SLF4JLogLevel resolveLevel(String level) {
        return switch (level == null ? "" : level.trim().toUpperCase()) {
            case "TRACE" -> SLF4JLogLevel.TRACE;
            case "INFO" -> SLF4JLogLevel.INFO;
            case "WARN" -> SLF4JLogLevel.WARN;
            case "ERROR" -> SLF4JLogLevel.ERROR;
            default -> SLF4JLogLevel.DEBUG;  // debug（默认）
        };
    }

    // ─── 私有工具 ───

    @Override
    public DataSource postProcess(DataSource dataSource, String dataSourceName) {
        ProxyDataSourceBuilder builder = ProxyDataSourceBuilder
                .create(dataSource)
                .name(dataSourceName);

        // ── 常规 SQL 日志 ──
        SLF4JLogLevel queryLevel = resolveLevel(properties.getLogLevel());
        if (properties.isMultiline()) {
            // 格式化输出：SQL 内关键字换行，便于人工阅读（1.10+ API：multiline() + logQueryBySlf4j()）
            builder.multiline().logQueryBySlf4j(queryLevel);
        } else {
            // 单行紧凑输出（默认）
            builder.logQueryBySlf4j(queryLevel);
        }

        // ── 慢查询告警 ──
        long slowThreshold = properties.getSlowQueryThreshold();
        if (slowThreshold > 0) {
            SLF4JLogLevel slowLevel = resolveLevel(properties.getSlowQueryLogLevel());
            builder.logSlowQueryBySlf4j(slowThreshold, TimeUnit.MILLISECONDS, slowLevel);
        }

        return builder.build();
    }
}


