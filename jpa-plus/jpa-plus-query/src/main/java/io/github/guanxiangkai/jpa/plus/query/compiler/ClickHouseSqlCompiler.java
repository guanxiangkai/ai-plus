package io.github.guanxiangkai.jpa.plus.query.compiler;

/**
 * ClickHouse 方言 SQL 编译器
 *
 * <p>ClickHouse 使用 {@code LIMIT rows OFFSET offset} 语法。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class ClickHouseSqlCompiler extends AbstractSqlCompiler {

    @Override
    protected void appendLimit(StringBuilder sql, Integer offset, Integer rows) {
        if (rows != null) {
            sql.append(" LIMIT ").append(rows);
        }
        if (offset != null && offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }
    }

    @Override
    protected AbstractSqlCompiler newInstance() {
        return new ClickHouseSqlCompiler();
    }
}

