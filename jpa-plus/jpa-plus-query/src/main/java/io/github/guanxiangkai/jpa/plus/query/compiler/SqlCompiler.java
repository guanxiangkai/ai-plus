package io.github.guanxiangkai.jpa.plus.query.compiler;

import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;

/**
 * SQL 编译器接口
 *
 * <p>将 {@link QueryContext}（包含条件 AST、排序、分页等信息）编译为可执行的原生 SQL。</p>
 *
 * <h3>内置方言实现</h3>
 * <table border="1">
 *   <tr><th>编译器</th><th>适用数据库</th><th>分页语法</th></tr>
 *   <tr><td>{@link MySqlCompiler}</td><td>MySQL 5.7+</td><td>{@code LIMIT offset, rows}</td></tr>
 *   <tr><td>{@link MariaDbSqlCompiler}</td><td>MariaDB 10.3+</td><td>{@code LIMIT offset, rows}</td></tr>
 *   <tr><td>{@link PgSqlCompiler}</td><td>PostgreSQL 9.5+</td><td>{@code LIMIT rows OFFSET offset}</td></tr>
 *   <tr><td>{@link OracleSqlCompiler}</td><td>Oracle 12c+</td><td>{@code OFFSET n ROWS FETCH NEXT m ROWS ONLY}</td></tr>
 *   <tr><td>{@link SqlServerSqlCompiler}</td><td>SQL Server 2012+</td><td>{@code OFFSET n ROWS FETCH NEXT m ROWS ONLY}</td></tr>
 *   <tr><td>{@link SqliteSqlCompiler}</td><td>SQLite 3.25+</td><td>{@code LIMIT rows OFFSET offset}</td></tr>
 *   <tr><td>{@link H2SqlCompiler}</td><td>H2（测试环境）</td><td>{@code LIMIT rows OFFSET offset}</td></tr>
 *   <tr><td>{@link ClickHouseSqlCompiler}</td><td>ClickHouse 21+</td><td>{@code LIMIT rows OFFSET offset}</td></tr>
 *   <tr><td>{@link OracleSqlCompiler} (达梦)</td><td>DM / 达梦（兼容 Oracle 语法）</td><td>{@code OFFSET n ROWS FETCH NEXT m ROWS ONLY}</td></tr>
 *   <tr><td>{@link PgSqlCompiler} (人大金仓)</td><td>KingbaseES（兼容 PostgreSQL 语法）</td><td>{@code LIMIT rows OFFSET offset}</td></tr>
 *   <tr><td>{@link DebugSqlCompiler}</td><td>装饰器（任意方言）</td><td>打印 SQL + 参数</td></tr>
 * </table>
 *
 * <h3>方言自动检测</h3>
 * <p>框架在启动时从 {@code DataSource} JDBC URL 自动识别方言，也可通过
 * {@code jpa-plus.dialect} 手动指定。详见 {@link SqlCompilerRegistry}。</p>
 *
 * <p><b>设计模式：</b>策略模式（Strategy） —— 不同数据库方言对应不同编译策略</p>
 *
 * @author guanxiangkai
 * @see SqlCompilerRegistry
 * @since 1.0.0
 */
@FunctionalInterface
public interface SqlCompiler {

    /**
     * 编译查询上下文为 SQL
     *
     * @param context 查询上下文（包含 AST、参数、排序、分页等）
     * @return SQL 编译结果（包含 SQL 语句和命名参数映射）
     */
    SqlResult compile(QueryContext context);
}
