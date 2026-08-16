package io.github.guanxiangkai.jpa.plus.starter;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动态数据源配置属性
 *
 * <p>通过 {@code spring.datasource.dynamic.*} 前缀配置，支持多数据源声明式定义：</p>
 * <pre>{@code
 * spring:
 *   datasource:
 *     dynamic:
 *       primary: master                   # 默认数据源
 *       strict: true                      # 严格模式
 *       hikari:                           # 全局连接池配置
 *         maximum-pool-size: 10
 *         minimum-idle: 5
 *         connection-timeout: 30000
 *         idle-timeout: 600000
 *         max-lifetime: 1800000
 *         pool-name: DynamicHikariCP
 *         connection-test-query: SELECT 1
 *       datasource:                       # 数据源列表
 *         master:
 *           url: ${APP_PRIMARY_DB_URL}
 *           username: ${APP_PRIMARY_DB_USERNAME}
 *           password: ${APP_PRIMARY_DB_PASSWORD}
 *           driver-class-name: com.mysql.cj.jdbc.Driver
 *           hikari:
 *             maximum-pool-size: 20
 *         slave_1:
 *           url: ${APP_REPLICA_DB_URL}
 *           username: ${APP_REPLICA_DB_USERNAME}
 *           password: ${APP_REPLICA_DB_PASSWORD}
 *         pg_db:
 *           url: ${APP_ANALYTICS_DB_URL}
 *           username: ${APP_ANALYTICS_DB_USERNAME}
 *           password: ${APP_ANALYTICS_DB_PASSWORD}
 *       refresh:
 *         enabled: true
 *         interval: 30s
 *         reset-pool: true
 *       jdbc:
 *         enabled: false
 *         table-name: jpa_plus_datasource
 *         auto-init-schema: true
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "spring.datasource.dynamic")
public class DynamicDataSourceProperties {

    /**
     * 默认数据源名称
     */
    @NotBlank(message = "spring.datasource.dynamic.primary 不能为空")
    private String primary = "master";

    /**
     * 严格模式：路由 key 不存在时抛异常而非回退到默认数据源
     */
    private boolean strict = false;

    /**
     * 是否懒加载数据源（启动时不初始化，首次使用时创建）
     */
    private boolean lazy = false;

    /**
     * 连接池类型
     * <ul>
     *   <li>{@code hikari}（默认）—— HikariCP，Spring Boot 默认内置</li>
     *   <li>{@code druid} —— Alibaba Druid，需在 classpath 中引入 {@code com.alibaba:druid}</li>
     * </ul>
     */
    private String poolType = "hikari";

    /**
     * datasource-proxy SQL 追踪配置（需在 classpath 中引入 {@code net.ttddyy:datasource-proxy}）
     */
    private DatasourceProxyProperties datasourceProxy = new DatasourceProxyProperties();

    /**
     * 路由策略配置
     */
    private RoutingProperties routing = new RoutingProperties();

    /**
     * 全局 HikariCP 连接池配置（各数据源可单独覆盖）
     */
    private HikariProperties hikari = new HikariProperties();

    /**
     * 数据源列表（key = 数据源名称，value = 连接配置）
     */
    private Map<String, DataSourceItemProperties> datasource = new LinkedHashMap<>();

    /**
     * 动态刷新配置
     */
    private RefreshProperties refresh = new RefreshProperties();

    /**
     * JDBC 数据源配置表（可选，与 YAML 数据源共存）
     */
    private JdbcProviderProperties jdbc = new JdbcProviderProperties();

    /**
     * 健康检查配置
     */
    private HealthProperties health = new HealthProperties();

    // ─────────── 内部配置类 ───────────

    /**
     * 路由策略枚举
     */
    public enum RoutingStrategy {
        /**
         * 通过 @DS 注解指定
         */
        ANNOTATION,
        /**
         * 从 HTTP 请求头获取
         */
        HEADER
    }

    /**
     * 路由策略属性
     */
    @Getter
    @Setter
    public static class RoutingProperties {

        /**
         * 数据源选择策略
         * <ul>
         *   <li>{@code ANNOTATION} —— 通过 {@code @DS("xxx")} 注解指定（默认）</li>
         *   <li>{@code HEADER} —— 从 HTTP 请求头获取</li>
         * </ul>
         */
        private RoutingStrategy strategy = RoutingStrategy.ANNOTATION;

        /**
         * {@code strategy=HEADER} 时的请求头名称
         */
        private String headerName = "X-DS";
    }

    /**
     * HikariCP 连接池属性（全局 / 单数据源级别共用此模型）
     */
    @Getter
    @Setter
    public static class HikariProperties {

        /**
         * 最大连接池大小
         */
        private Integer maximumPoolSize;

        /**
         * 最小空闲连接数
         */
        private Integer minimumIdle;

        /**
         * 获取连接超时（毫秒）
         */
        private Long connectionTimeout;

        /**
         * 空闲连接超时（毫秒）
         */
        private Long idleTimeout;

        /**
         * 连接最大存活时间（毫秒）
         */
        private Long maxLifetime;

        /**
         * 连接池名称
         */
        private String poolName;

        /**
         * 连接校验 SQL
         */
        private String connectionTestQuery;

        /**
         * 连接校验超时（毫秒）
         */
        private Long validationTimeout;

        /**
         * 连接泄漏检测阈值（毫秒），0 表示禁用
         */
        private Long leakDetectionThreshold;

        /**
         * 是否注册 JMX MBean
         */
        private Boolean registerMbeans;
    }

    /**
     * 单个数据源配置
     */
    @Getter
    @Setter
    public static class DataSourceItemProperties {

        /**
         * JDBC URL
         */
        private String url;

        /**
         * 数据库用户名
         */
        private String username;

        /**
         * 数据库密码
         */
        private String password;

        /**
         * JDBC 驱动类名（可选，框架可根据 URL 自动检测）
         */
        private String driverClassName;

        /**
         * 单数据源级别的 HikariCP 配置覆盖（覆盖全局 hikari 配置）
         */
        private HikariProperties hikari;
    }

    /**
     * 刷新监控配置
     */
    @Getter
    @Setter
    public static class RefreshProperties {

        /**
         * 是否开启刷新监控
         */
        private boolean enabled = false;

        /**
         * 轮询间隔（默认 30 秒）
         */
        private Duration interval = Duration.ofSeconds(30);

        /**
         * 刷新时是否重置连接池
         */
        private boolean resetPool = true;
    }

    /**
     * JDBC 配置表提供者配置
     */
    @Getter
    @Setter
    public static class JdbcProviderProperties {

        /**
         * 是否启用 JDBC 配置表提供者
         */
        private boolean enabled = false;

        /**
         * 配置表名
         */
        private String tableName = "jpa_plus_datasource";

        /**
         * 是否自动建表
         */
        private boolean autoInitSchema = true;
    }

    /**
     * 健康检查配置
     */
    @Getter
    @Setter
    public static class HealthProperties {

        /**
         * 是否暴露数据源健康检查（配合 Spring Boot Actuator）
         */
        private boolean enabled = true;

        /**
         * 是否在健康信息中包含各数据源详情
         */
        private boolean includeDetail = true;
    }

    /**
     * datasource-proxy SQL 追踪配置
     *
     * <p>启用后，所有通过动态数据源发出的 JDBC SQL 均会被代理拦截并以 SLF4J 日志输出，
     * 包含实际参数值、执行耗时，支持慢查询阈值告警。
     * 相比 {@code spring.jpa.show-sql}，拦截范围覆盖所有 JDBC 调用（含 Hibernate 生成的 SQL）。</p>
     *
     * <h3>激活方式</h3>
     * <pre>{@code
     * spring:
     *   datasource:
     *     dynamic:
     *       datasource-proxy:
     *         enabled: true
     *         log-level: debug
     *         multiline: false
     *         slow-query-threshold: 2000
     * }</pre>
     * <p>同时需要在 classpath 中引入：{@code runtimeOnly("net.ttddyy:datasource-proxy:1.10")}</p>
     */
    @Getter
    @Setter
    public static class DatasourceProxyProperties {

        /**
         * 是否启用 datasource-proxy SQL 追踪（需 classpath 中有 net.ttddyy:datasource-proxy）
         */
        private boolean enabled = false;

        /**
         * 常规 SQL 输出的 SLF4J 日志级别
         * <ul>
         *   <li>{@code trace}</li>
         *   <li>{@code debug}（默认）</li>
         *   <li>{@code info}</li>
         *   <li>{@code warn}</li>
         * </ul>
         */
        private String logLevel = "debug";

        /**
         * 是否以多行格式输出 SQL（对 SQL 关键字换行，便于人工阅读）
         * <p>默认 {@code false}（单行紧凑输出，适合日志采集）</p>
         */
        private boolean multiline = false;

        /**
         * 慢查询阈值（毫秒）
         * <p>超过此值时以 {@link #slowQueryLogLevel} 输出告警日志；
         * 设为 {@code 0}（默认）表示禁用慢查询检测。</p>
         */
        private long slowQueryThreshold = 0L;

        /**
         * 慢查询日志级别（默认 {@code warn}）
         */
        private String slowQueryLogLevel = "warn";
    }
}
