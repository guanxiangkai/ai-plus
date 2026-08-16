package io.github.guanxiangkai.jpa.plus.datasource.model;

import io.github.guanxiangkai.jpa.plus.datasource.enums.DatabaseType;
import io.github.guanxiangkai.jpa.plus.datasource.enums.DbType;

import java.util.Objects;

/**
 * 数据源定义（不可变值对象）
 *
 * <p>描述一个数据源的连接属性和连接池配置，用于运行时动态创建或刷新数据源。
 * 支持从 JDBC URL 自动检测 {@link DatabaseType}，驱动类名和校验 SQL 亦可自动推导。</p>
 *
 * <h3>自动检测</h3>
 * <ul>
 *   <li>{@code dbType} 为 null → 根据 {@code url} 自动检测（如 {@code jdbc:mysql:} → {@link DatabaseType#MYSQL}）</li>
 *   <li>{@code driverClassName} 为空 → 由 {@code dbType} 提供</li>
 *   <li>{@code connectionTestQuery} 为空 → 由 {@code dbType} 提供</li>
 *   <li>{@code poolName} 为空 → 自动生成 {@code jpa-plus-{name}}</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * String url = System.getenv("APP_REPLICA_DB_URL");
 * String username = System.getenv("APP_REPLICA_DB_USERNAME");
 * String password = System.getenv("APP_REPLICA_DB_PASSWORD");
 *
 * // 最简形式（自动检测 DatabaseType、驱动、校验 SQL）
 * var def = DataSourceDefinition.of("replica", url, username, password);
 *
 * // 完整配置
 * var def = new DataSourceDefinition("replica", DatabaseType.MYSQL,
 *     url, username, password,
 *     null, 5, 20, 30_000L, 600_000L, 1_800_000L, null, null,
 *     5_000L, 0L, false);
 * }</pre>
 *
 * @param name                   数据源名称（路由 key）
 * @param dbType                 数据库类型（可为 null，自动从 URL 检测）
 * @param url                    JDBC URL
 * @param username               数据库用户名
 * @param password               数据库密码
 * @param driverClassName        JDBC 驱动类名（可为 null，由 dbType 提供）
 * @param minimumIdle            最小空闲连接数
 * @param maximumPoolSize        最大连接池大小
 * @param connectionTimeout      连接超时（毫秒）
 * @param idleTimeout            空闲连接超时（毫秒）
 * @param maxLifetime            连接最大存活时间（毫秒）
 * @param poolName               连接池名称（可为 null，自动生成）
 * @param connectionTestQuery    连接校验 SQL（可为 null，由 dbType 提供）
 * @param validationTimeout      连接校验超时（毫秒）
 * @param leakDetectionThreshold 连接泄漏检测阈值（毫秒），0 表示禁用
 * @param registerMbeans         是否注册 JMX MBean
 * @author guanxiangkai
 * @since 1.0.0
 */
public record DataSourceDefinition(
        String name,
        DbType dbType,
        String url,
        String username,
        String password,
        String driverClassName,
        int minimumIdle,
        int maximumPoolSize,
        long connectionTimeout,
        long idleTimeout,
        long maxLifetime,
        String poolName,
        String connectionTestQuery,
        long validationTimeout,
        long leakDetectionThreshold,
        boolean registerMbeans
) {

    /**
     * 紧凑规范构造器 —— 自动检测 dbType / driverClassName / poolName / connectionTestQuery
     */
    public DataSourceDefinition {
        Objects.requireNonNull(name, "数据源名称不能为空");
        Objects.requireNonNull(url, "数据源 URL 不得为空");

        // lambda 要求 effectively final，需在赋值前捕获
        String originalDriver = driverClassName;

        // 自动检测 DatabaseType
        if (dbType == null) {
            dbType = DatabaseType.fromUrl(url)
                    .map(DbType.class::cast)
                    .or(() -> DatabaseType.fromDriverClassName(originalDriver).map(DbType.class::cast))
                    .orElse(null);
        }

        // 自动推导驱动类名
        if ((driverClassName == null || driverClassName.isBlank()) && dbType != null) {
            driverClassName = dbType.driverClassName();
        }

        // 自动推导校验 SQL
        if ((connectionTestQuery == null || connectionTestQuery.isBlank()) && dbType != null) {
            connectionTestQuery = dbType.validationQuery();
        }

        // 自动生成连接池名称
        if (poolName == null || poolName.isBlank()) {
            poolName = "jpa-plus-" + name;
        }
    }

    // ─── 静态工厂方法 ───

    /**
     * 最简形式 —— 自动检测一切
     *
     * @param name     数据源名称
     * @param url      JDBC URL
     * @param username 数据库用户名
     * @param password 数据库密码
     * @return 数据源定义
     */
    public static DataSourceDefinition of(String name, String url, String username, String password) {
        return new DataSourceDefinition(name, null, url, username, password,
                null, 5, 20, 30_000L, 600_000L, 1_800_000L, null, null,
                5_000L, 0L, false);
    }

    /**
     * 返回不包含连接地址和凭据的诊断摘要。
     *
     * @return 已脱敏的数据源定义摘要
     */
    @Override
    public String toString() {
        return "DataSourceDefinition[name=" + name
                + ", dbType=" + dbType
                + ", connection=<redacted>"
                + ", poolName=" + poolName
                + ", minimumIdle=" + minimumIdle
                + ", maximumPoolSize=" + maximumPoolSize + ']';
    }
}
