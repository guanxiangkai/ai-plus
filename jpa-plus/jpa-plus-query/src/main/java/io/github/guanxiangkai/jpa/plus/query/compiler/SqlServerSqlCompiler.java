package io.github.guanxiangkai.jpa.plus.query.compiler;

/**
 * SQL Server 方言 SQL 编译器
 *
 * <p>支持 SQL Server 2012+ 的 OFFSET/FETCH 分页语法：
 * {@code OFFSET n ROWS FETCH NEXT m ROWS ONLY}。</p>
 *
 * <p><b>注意：</b>当使用 FETCH NEXT 时，OFFSET 子句是必须的（即使为 0）。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class SqlServerSqlCompiler extends AbstractSqlCompiler {

    @Override
    protected void appendLimit(StringBuilder sql, Integer offset, Integer rows) {
        if (rows != null) {
            // FETCH NEXT 必须搭配 OFFSET
            int off = (offset != null) ? offset : 0;
            sql.append(" OFFSET ").append(off).append(" ROWS");
            sql.append(" FETCH NEXT ").append(rows).append(" ROWS ONLY");
        } else if (offset != null && offset > 0) {
            sql.append(" OFFSET ").append(offset).append(" ROWS");
        }
    }

    @Override
    protected AbstractSqlCompiler newInstance() {
        return new SqlServerSqlCompiler();
    }
}

