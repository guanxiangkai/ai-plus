package io.github.guanxiangkai.jpa.plus.query.compiler;

/**
 * H2 方言 SQL 编译器
 *
 * <p>H2 使用 {@code LIMIT rows OFFSET offset} 语法，与 PostgreSQL 相同。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class H2SqlCompiler extends AbstractSqlCompiler {

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
        return new H2SqlCompiler();
    }
}

