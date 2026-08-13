package io.github.guanxiangkai.jpa.plus.query.compiler;

/**
 * PostgreSQL 方言 SQL 编译器
 *
 * <p>继承 {@link AbstractSqlCompiler}，仅实现 PostgreSQL 特有的 LIMIT/OFFSET 语法：
 * {@code LIMIT rows OFFSET offset}。</p>
 *
 * <p><b>设计模式：</b>
 * <ul>
 *   <li>模板方法模式（Template Method） —— 继承抽象编译器，覆盖方言差异</li>
 *   <li>策略模式（Strategy） —— 作为 {@link SqlCompiler} 的 PostgreSQL 策略实现</li>
 * </ul>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class PgSqlCompiler extends AbstractSqlCompiler {

    @Override
    protected void appendLimit(StringBuilder sql, Integer offset, Integer rows) {
        // PostgreSQL 使用 LIMIT ... OFFSET ... 语法
        if (rows != null) {
            sql.append(" LIMIT ").append(rows);
        }
        if (offset != null && offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }
    }

    @Override
    protected AbstractSqlCompiler newInstance() {
        return new PgSqlCompiler();
    }
}

