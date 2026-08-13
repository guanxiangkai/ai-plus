package io.github.guanxiangkai.jpa.plus.query.compiler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * SQL 编译器注册表
 *
 * <p>根据 JDBC URL 前缀或方言名称自动解析对应方言的 {@link SqlCompiler}。
 * 2.0 起未知方言直接抛错，避免用错误 SQL 方言静默执行。</p>
 *
 * <h3>自动探测逻辑（优先级由高到低）</h3>
 * <ol>
 *   <li>{@code jpa-plus.dialect} 配置项手动指定</li>
 *   <li>从 {@code DataSource} JDBC URL 前缀自动识别</li>
 * </ol>
 *
 * <h3>支持的方言</h3>
 * <table border="1">
 *   <tr><th>方言</th><th>JDBC URL 前缀</th><th>分页语法</th></tr>
 *   <tr><td>MySQL</td><td>{@code jdbc:mysql:}</td><td>{@code LIMIT offset, rows}</td></tr>
 *   <tr><td>MariaDB</td><td>{@code jdbc:mariadb:}</td><td>{@code LIMIT offset, rows}</td></tr>
 *   <tr><td>PostgreSQL</td><td>{@code jdbc:postgresql:}</td><td>{@code LIMIT rows OFFSET offset}</td></tr>
 *   <tr><td>Oracle</td><td>{@code jdbc:oracle:}</td><td>{@code OFFSET n ROWS FETCH NEXT m ROWS ONLY}</td></tr>
 *   <tr><td>SQL Server</td><td>{@code jdbc:sqlserver:}</td><td>{@code OFFSET n ROWS FETCH NEXT m ROWS ONLY}</td></tr>
 *   <tr><td>SQLite</td><td>{@code jdbc:sqlite:}</td><td>{@code LIMIT rows OFFSET offset}</td></tr>
 *   <tr><td>H2</td><td>{@code jdbc:h2:}</td><td>{@code LIMIT rows OFFSET offset}</td></tr>
 *   <tr><td>ClickHouse</td><td>{@code jdbc:clickhouse:}</td><td>{@code LIMIT rows OFFSET offset}</td></tr>
 * </table>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class SqlCompilerRegistry {

    private static final String SUPPORTED_DIALECTS =
            "mysql, mariadb, postgresql/postgres/pg, oracle, sqlserver/mssql/sql_server/sql-server, sqlite, h2, clickhouse";

    /**
     * JDBC URL 前缀（小写）→ 编译器工厂，顺序敏感（mariadb 在 mysql 前防止前缀误匹配）
     */
    private static final Map<String, Supplier<AbstractSqlCompiler>> URL_REGISTRY = buildUrlRegistry();

    private SqlCompilerRegistry() {
    }

    /**
     * 根据 JDBC URL 自动匹配编译器
     *
     * @param jdbcUrl JDBC URL（如 {@code jdbc:postgresql://localhost/mydb}）
     * @return 对应方言的编译器实例
     * @throws IllegalArgumentException JDBC URL 为空或无法匹配已知方言
     */
    public static AbstractSqlCompiler resolve(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "[jpa-plus] Cannot resolve SQL dialect because JDBC URL is blank. " +
                            "Configure 'jpa-plus.dialect' explicitly. Supported dialects: " + SUPPORTED_DIALECTS);
        }
        String lower = jdbcUrl.toLowerCase();
        for (Map.Entry<String, Supplier<AbstractSqlCompiler>> entry : URL_REGISTRY.entrySet()) {
            if (lower.startsWith(entry.getKey())) {
                return entry.getValue().get();
            }
        }
        throw new IllegalArgumentException(
                "[jpa-plus] Unsupported JDBC URL for SQL dialect detection: " + jdbcUrl +
                        ". Supported JDBC prefixes: " + String.join(", ", URL_REGISTRY.keySet()) +
                        ". Or configure 'jpa-plus.dialect'.");
    }

    /**
     * 根据方言名称解析编译器，用于 {@code jpa-plus.dialect} 配置项手动指定
     *
     * @param dialect 方言名称（不区分大小写），如 {@code mysql}、{@code oracle}、{@code postgresql}
     * @return 对应方言的编译器实例
     * @throws IllegalArgumentException 方言为空或未知
     */
    public static AbstractSqlCompiler resolveByDialect(String dialect) {
        if (dialect == null || dialect.isBlank()) {
            throw new IllegalArgumentException(
                    "[jpa-plus] SQL dialect is blank. Supported dialects: " + SUPPORTED_DIALECTS);
        }
        return switch (dialect.trim().toLowerCase()) {
            case "mysql" -> new MySqlCompiler();
            case "mariadb" -> new MariaDbSqlCompiler();
            case "postgresql", "postgres", "pg" -> new PgSqlCompiler();
            case "oracle" -> new OracleSqlCompiler();
            case "sqlserver", "mssql",
                 "sql_server", "sql-server" -> new SqlServerSqlCompiler();
            case "sqlite" -> new SqliteSqlCompiler();
            case "h2" -> new H2SqlCompiler();
            case "clickhouse" -> new ClickHouseSqlCompiler();
            default -> throw new IllegalArgumentException(
                    "[jpa-plus] Unsupported SQL dialect: " + dialect.trim() +
                            ". Supported dialects: " + SUPPORTED_DIALECTS);
        };
    }

    private static Map<String, Supplier<AbstractSqlCompiler>> buildUrlRegistry() {
        LinkedHashMap<String, Supplier<AbstractSqlCompiler>> registry = new LinkedHashMap<>();
        registry.put("jdbc:mariadb:", MariaDbSqlCompiler::new);
        registry.put("jdbc:mysql:", MySqlCompiler::new);
        registry.put("jdbc:postgresql:", PgSqlCompiler::new);
        registry.put("jdbc:oracle:", OracleSqlCompiler::new);
        registry.put("jdbc:sqlserver:", SqlServerSqlCompiler::new);
        registry.put("jdbc:sqlite:", SqliteSqlCompiler::new);
        registry.put("jdbc:h2:", H2SqlCompiler::new);
        registry.put("jdbc:clickhouse:", ClickHouseSqlCompiler::new);
        registry.put("jdbc:dm:", OracleSqlCompiler::new);
        registry.put("jdbc:kingbase8:", PgSqlCompiler::new);
        return Collections.unmodifiableMap(registry);
    }
}
