package io.github.guanxiangkai.jpa.plus.query.context;

import io.github.guanxiangkai.jpa.plus.query.ast.Condition;
import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询运行时（不可变值对象）
 *
 * <p>使用 Java {@code record} 保证不可变性。
 * 拦截器可通过 {@code with*} 方法创建修改后的新实例（Wither 模式）。</p>
 *
 * <p><b>设计模式：</b>不可变值对象模式（Immutable Value Object）</p>
 *
 * @param where      WHERE 条件 AST
 * @param orderBys   排序列表
 * @param parameters 额外参数
 * @param offset     分页偏移量
 * @param rows       分页行数
 * @param groupBys   GROUP BY 列列表（为空时不生成 GROUP BY 子句）
 * @param having     HAVING 条件 AST（可为 {@code null}）
 * @author guanxiangkai
 * @since 1.0.0
 */
public record QueryRuntime(
        Condition where,
        List<OrderBy> orderBys,
        Map<String, Object> parameters,
        Integer offset,
        Integer rows,
        List<ColumnMeta> groupBys,
        Condition having
) {

    public QueryRuntime() {
        this(null, List.of(), Map.of(), null, null, List.of(), null);
    }

    public QueryRuntime(Condition where, List<OrderBy> orderBys, Map<String, Object> parameters,
                        Integer offset, Integer rows, List<ColumnMeta> groupBys, Condition having) {
        this.where = where;
        this.orderBys = List.copyOf(orderBys);
        this.parameters = Map.copyOf(parameters);
        this.offset = offset;
        this.rows = rows;
        this.groupBys = groupBys == null ? List.of() : List.copyOf(groupBys);
        this.having = having;
    }

    public QueryRuntime withWhere(Condition where) {
        return new QueryRuntime(where, orderBys, parameters, offset, rows, groupBys, having);
    }

    public QueryRuntime withParameter(String key, Object value) {
        Map<String, Object> newParams = new LinkedHashMap<>(parameters);
        newParams.put(key, value);
        return new QueryRuntime(where, orderBys, newParams, offset, rows, groupBys, having);
    }

    public QueryRuntime withOrderBys(List<OrderBy> orderBys) {
        return new QueryRuntime(where, orderBys, parameters, offset, rows, groupBys, having);
    }

    public QueryRuntime withLimit(Integer offset, Integer rows) {
        return new QueryRuntime(where, orderBys, parameters, offset, rows, groupBys, having);
    }

    public QueryRuntime withGroupBys(List<ColumnMeta> groupBys) {
        return new QueryRuntime(where, orderBys, parameters, offset, rows, groupBys, having);
    }

    public QueryRuntime withHaving(Condition having) {
        return new QueryRuntime(where, orderBys, parameters, offset, rows, groupBys, having);
    }
}
