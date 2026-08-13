package io.github.guanxiangkai.jpa.plus.query.compiler;

/**
 * MariaDB 方言 SQL 编译器
 *
 * <p>MariaDB 与 MySQL 使用相同的 {@code LIMIT offset, rows} 语法。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class MariaDbSqlCompiler extends AbstractSqlCompiler {

    @Override
    protected void appendLimit(StringBuilder sql, Integer offset, Integer rows) {
        if (offset != null && rows != null) {
            sql.append(" LIMIT ").append(offset).append(", ").append(rows);
        } else if (rows != null) {
            sql.append(" LIMIT ").append(rows);
        }
    }

    @Override
    protected AbstractSqlCompiler newInstance() {
        return new MariaDbSqlCompiler();
    }
}

