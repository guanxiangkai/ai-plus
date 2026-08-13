package io.github.guanxiangkai.jpa.plus.query.compiler;

/**
 * Oracle 方言 SQL 编译器
 *
 * <p>支持 Oracle 12c+ 的标准 ANSI OFFSET/FETCH 分页语法：
 * <ul>
 *   <li>仅分页：{@code OFFSET n ROWS FETCH NEXT m ROWS ONLY}</li>
 *   <li>仅跳过：{@code OFFSET n ROWS}</li>
 *   <li>取前 N 行：{@code FETCH FIRST m ROWS ONLY}</li>
 * </ul>
 * </p>
 *
 * <p><b>注意：</b>Oracle 11g 及以下使用 {@code ROWNUM} 子查询方式，需升级到 12c+
 * 或由用户手动覆盖此 Bean 提供兼容实现。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class OracleSqlCompiler extends AbstractSqlCompiler {

    @Override
    protected void appendLimit(StringBuilder sql, Integer offset, Integer rows) {
        if (offset != null && offset > 0) {
            sql.append(" OFFSET ").append(offset).append(" ROWS");
        }
        if (rows != null) {
            if (offset != null && offset > 0) {
                sql.append(" FETCH NEXT ").append(rows).append(" ROWS ONLY");
            } else {
                sql.append(" FETCH FIRST ").append(rows).append(" ROWS ONLY");
            }
        }
    }

    @Override
    protected AbstractSqlCompiler newInstance() {
        return new OracleSqlCompiler();
    }
}

