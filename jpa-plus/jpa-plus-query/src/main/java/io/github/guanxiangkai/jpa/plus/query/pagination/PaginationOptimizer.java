package io.github.guanxiangkai.jpa.plus.query.pagination;

import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;
import io.github.guanxiangkai.jpa.plus.query.context.QueryRuntime;

import java.util.List;

/**
 * 分页优化器
 * <p>
 * 自动检测查询类型，选择最优的 COUNT 策略：
 * - SIMPLE：去掉 ORDER BY 和 LIMIT，直接 COUNT
 * - SUBQUERY：包装为子查询后 COUNT
 * - FORCE_SUBQUERY：强制子查询
 */
public class PaginationOptimizer {

    private final CountStrategy defaultStrategy;

    public PaginationOptimizer() {
        this(CountStrategy.SIMPLE);
    }

    public PaginationOptimizer(CountStrategy defaultStrategy) {
        this.defaultStrategy = defaultStrategy;
    }

    /**
     * 构建用于 COUNT 的查询上下文
     */
    public QueryContext buildCountContext(QueryContext originalCtx) {
        CountStrategy strategy = detectStrategy(originalCtx);
        return switch (strategy) {
            case SIMPLE -> buildSimpleCount(originalCtx);
            case SUBQUERY, FORCE_SUBQUERY -> buildSubqueryCount(originalCtx);
        };
    }

    /**
     * 检测最优 COUNT 策略
     */
    public CountStrategy detectStrategy(QueryContext ctx) {
        // GROUP BY 会改变行数基数，必须用子查询包装后再 COUNT
        if (!ctx.runtime().groupBys().isEmpty()) {
            return CountStrategy.SUBQUERY;
        }
        // 如果有 join 可能影响行数
        if (ctx.metadata().joinGraph() != null && ctx.metadata().joinGraph().hasJoins()) {
            return CountStrategy.SUBQUERY;
        }
        return defaultStrategy;
    }

    private QueryContext buildSimpleCount(QueryContext originalCtx) {
        // 去掉 ORDER BY 和 LIMIT
        QueryRuntime newRuntime = originalCtx.runtime()
                .withOrderBys(List.of())
                .withLimit(null, null);
        return originalCtx.withRuntime(newRuntime);
    }

    private QueryContext buildSubqueryCount(QueryContext originalCtx) {
        // 去掉 ORDER BY 和 LIMIT（子查询包装由 SqlCompiler 在 COUNT 模式处理）
        QueryRuntime newRuntime = originalCtx.runtime()
                .withOrderBys(List.of())
                .withLimit(null, null);
        return originalCtx.withRuntime(newRuntime);
    }
}

