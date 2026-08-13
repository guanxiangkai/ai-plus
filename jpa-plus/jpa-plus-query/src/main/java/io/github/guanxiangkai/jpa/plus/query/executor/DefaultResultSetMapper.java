package io.github.guanxiangkai.jpa.plus.query.executor;

import io.github.guanxiangkai.jpa.plus.query.plan.MappingPlan;
import io.github.guanxiangkai.jpa.plus.query.plan.MappingPlanCompiler;
import io.github.guanxiangkai.jpa.plus.query.wrapper.SelectColumn;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 默认结果集映射器
 * <p>
 * 使用 MappingPlan 进行编译期优化，避免运行时反射开销。
 */
public class DefaultResultSetMapper<R> implements ResultSetMapper<R> {

    private final MappingPlan<R> plan;

    public DefaultResultSetMapper(Class<R> targetType, List<SelectColumn> columns) {
        this.plan = MappingPlanCompiler.compile(targetType, columns);
    }

    @Override
    public R map(ResultSet rs, MappingContext context) throws SQLException {
        return plan.apply(rs);
    }
}

