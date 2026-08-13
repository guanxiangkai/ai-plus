package io.github.guanxiangkai.jpa.plus.query.compiler;

/**
 * MySQL 方言 SQL 编译器
 *
 * <p>继承 {@link AbstractSqlCompiler}，仅实现 MySQL 特有的 LIMIT 语法：
 * {@code LIMIT offset, rows}。</p>
 *
 * <p><b>设计模式：</b>
 * <ul>
 *   <li>模板方法模式（Template Method） —— 继承抽象编译器，覆盖方言差异</li>
 *   <li>策略模式（Strategy） —— 作为 {@link SqlCompiler} 的 MySQL 策略实现</li>
 * </ul>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class MySqlCompiler extends AbstractSqlCompiler {

    @Override
    protected void appendLimit(StringBuilder sql, Integer offset, Integer rows) {
        if (offset != null && rows != null) {
            sql.append(" LIMIT ").append(offset).append(", ").append(rows);
        }
    }

    @Override
    protected AbstractSqlCompiler newInstance() {
        return new MySqlCompiler();
    }
}

