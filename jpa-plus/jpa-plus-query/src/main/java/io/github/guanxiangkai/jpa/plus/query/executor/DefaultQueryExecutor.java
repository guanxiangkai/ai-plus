package io.github.guanxiangkai.jpa.plus.query.executor;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;
import io.github.guanxiangkai.jpa.plus.query.ast.*;
import io.github.guanxiangkai.jpa.plus.query.compiler.SqlCompiler;
import io.github.guanxiangkai.jpa.plus.query.compiler.SqlResult;
import io.github.guanxiangkai.jpa.plus.query.context.OrderBy;
import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;
import io.github.guanxiangkai.jpa.plus.query.pagination.KeysetCursor;
import io.github.guanxiangkai.jpa.plus.query.pagination.KeysetPageResult;
import io.github.guanxiangkai.jpa.plus.query.pagination.PageResult;
import io.github.guanxiangkai.jpa.plus.query.pagination.PaginationOptimizer;
import io.github.guanxiangkai.jpa.plus.query.wrapper.JoinWrapper;
import io.github.guanxiangkai.jpa.plus.query.wrapper.QueryWrapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 默认查询执行器
 *
 * <p>通过 {@link SqlCompiler} 编译 SQL，使用 {@code EntityManager.createNativeQuery()} 执行。
 * 强制使用命名参数绑定，确保 SQL 注入安全。</p>
 *
 * <h3>慢 SQL 检测</h3>
 * <p>通过构造器注入 {@code slowSqlThresholdMs}（毫秒），当 SQL 执行耗时超过阈值时
 * 输出 WARN 级别日志。阈值为 {@code 0} 时禁用检测（零开销）。</p>
 *
 * <h3>配置方式</h3>
 * <pre>{@code
 * jpa-plus:
 *   debug:
 *     slow-sql:
 *       enabled: true
 *       threshold: 1000   # 毫秒
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class DefaultQueryExecutor implements QueryExecutor, QueryContextExecutor {

    private final EntityManager entityManager;
    private final SqlCompiler sqlCompiler;
    private final PaginationOptimizer paginationOptimizer;
    private final KeysetCursorExtractor keysetCursorExtractor = new KeysetCursorExtractor();

    /**
     * 慢 SQL 检测阈值（毫秒），{@code 0} 表示禁用
     */
    private final long slowSqlThresholdMs;
    private final int streamFetchSize;

    public DefaultQueryExecutor(EntityManager entityManager,
                                SqlCompiler sqlCompiler,
                                PaginationOptimizer paginationOptimizer) {
        this(entityManager, sqlCompiler, paginationOptimizer, 0L, 500);
    }

    public DefaultQueryExecutor(EntityManager entityManager,
                                SqlCompiler sqlCompiler,
                                PaginationOptimizer paginationOptimizer,
                                long slowSqlThresholdMs) {
        this(entityManager, sqlCompiler, paginationOptimizer, slowSqlThresholdMs, 500);
    }

    public DefaultQueryExecutor(EntityManager entityManager,
                                SqlCompiler sqlCompiler,
                                PaginationOptimizer paginationOptimizer,
                                long slowSqlThresholdMs,
                                int streamFetchSize) {
        this.entityManager = entityManager;
        this.sqlCompiler = sqlCompiler;
        this.paginationOptimizer = paginationOptimizer;
        this.slowSqlThresholdMs = slowSqlThresholdMs;
        this.streamFetchSize = Math.max(1, streamFetchSize);
    }

    /**
     * P1-28: Safe conversion of pageable offset to int.
     * Throws JpaPlusException for offsets that exceed Integer.MAX_VALUE
     * rather than silently overflowing via Math.toIntExact.
     */
    private static int safeToIntOffset(long offset) {
        if (offset < 0 || offset > Integer.MAX_VALUE) {
            throw new JpaPlusException(
                    "Page offset " + offset + " exceeds the maximum supported value (" +
                            Integer.MAX_VALUE + "). Reduce the page number or use keyset pagination.");
        }
        return (int) offset;
    }

    private static <T> PageResult<T> pageResult(long total,
                                                Pageable pageable,
                                                Supplier<List<T>> recordsSupplier) {
        if (total == 0) {
            return PageResult.empty(pageable.getPageNumber() + 1, pageable.getPageSize());
        }
        return new PageResult<>(
                recordsSupplier.get(),
                total,
                pageable.getPageNumber() + 1,
                pageable.getPageSize()
        );
    }

    @Override
    public <T> List<T> list(QueryWrapper<T> wrapper) {
        return list(wrapper.buildContext(), wrapper.getEntityClass());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> list(QueryContext context, Class<T> resultType) {
        SqlResult result = sqlCompiler.compile(context);
        Query query = createNativeQuery(result, resultType);
        long start = System.currentTimeMillis();
        List<T> list = query.getResultList();
        checkSlowSql(result.sql(), System.currentTimeMillis() - start);
        return list;
    }

    @Override
    public <T> Optional<T> one(QueryWrapper<T> wrapper) {
        QueryWrapper<T> limited = wrapper.limit(0, 1);
        List<T> list = list(limited);
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.getFirst());
    }

    @Override
    public long count(QueryWrapper<?> wrapper) {
        return count(wrapper.buildContext());
    }

    @Override
    public long count(QueryContext context) {
        QueryContext countCtx = paginationOptimizer.buildCountContext(context);
        SqlResult result = sqlCompiler.compile(countCtx);

        String countSql = "SELECT COUNT(*) FROM (" + result.sql() + ") _count_tmp";
        Query query = entityManager.createNativeQuery(countSql);
        bindParameters(query, result.params());

        long start = System.currentTimeMillis();
        Object count = query.getSingleResult();
        checkSlowSql(countSql, System.currentTimeMillis() - start);
        if (count instanceof Number n) {
            return n.longValue();
        }
        throw new JpaPlusException(
                "COUNT(*) 返回了非数值类型: " + (count == null ? "null" : count.getClass().getName()));
    }

    @Override
    public <T> PageResult<T> page(QueryWrapper<T> wrapper, Pageable pageable) {
        return pageResult(count(wrapper), pageable, () -> {
            QueryWrapper<T> paged = wrapper.limit(safeToIntOffset(pageable.getOffset()), pageable.getPageSize());
            return list(paged);
        });
    }

    @Override
    public <R> List<R> list(JoinWrapper<?> wrapper, Class<R> resultType) {
        return list(wrapper.buildContext(), resultType);
    }

    @Override
    public <R> Optional<R> one(JoinWrapper<?> wrapper, Class<R> resultType) {
        JoinWrapper<?> limited = wrapper.limit(0, 1);
        List<R> list = list(limited, resultType);
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.getFirst());
    }

    @Override
    public <R> PageResult<R> page(JoinWrapper<?> wrapper, Class<R> resultType, Pageable pageable) {
        QueryContext ctx = wrapper.buildContext();
        long total = count(ctx);
        return pageResult(total, pageable, () -> {
            JoinWrapper<?> paged = wrapper.limit(safeToIntOffset(pageable.getOffset()), pageable.getPageSize());
            return list(paged, resultType);
        });
    }

    @Override
    public <T> KeysetPageResult<T> pageKeyset(QueryWrapper<T> wrapper, KeysetCursor cursor) {
        // 注入 Keyset 条件到 WHERE，使查询从游标位置开始
        QueryWrapper<T> seekWrapper = applyKeysetCondition(wrapper, cursor);
        // 多取一条用于探测 hasNext
        QueryWrapper<T> paged = seekWrapper.limit(0, cursor.pageSize() + 1);

        QueryContext ctx = paged.buildContext();
        List<T> rows = list(ctx, wrapper.getEntityClass());

        boolean hasNext = rows.size() > cursor.pageSize();
        List<T> content = hasNext ? rows.subList(0, cursor.pageSize()) : rows;

        // 从最后一条记录提取下一页游标值
        KeysetCursor nextCursor = hasNext
                ? keysetCursorExtractor.buildNextCursor(content.getLast(), ctx.runtime().orderBys(), cursor.pageSize())
                : KeysetCursor.first(cursor.pageSize());

        return new KeysetPageResult<>(content, nextCursor, hasNext);
    }

    /**
     * 将 Keyset 游标转换为完整的词法比较（Lexicographic Seek）条件注入 wrapper。
     *
     * <p>对于排序列 [col1 ASC, col2 ASC, col3 ASC] 和游标值 [v1, v2, v3]，
     * 生成的条件为：<pre>
     *   (col1 &gt; v1)
     *   OR (col1 = v1 AND col2 &gt; v2)
     *   OR (col1 = v1 AND col2 = v2 AND col3 &gt; v3)
     * </pre>
     * DESC 列使用 {@code <} 代替 {@code >}。
     * 若某列的游标值缺失，截断到该列前的所有排序列（保守求解）。</p>
     */
    private <T> QueryWrapper<T> applyKeysetCondition(QueryWrapper<T> wrapper, KeysetCursor cursor) {
        if (cursor.isFirst()) return wrapper;

        QueryContext base = wrapper.buildContext();
        List<OrderBy> orderBys = base.runtime().orderBys();
        if (orderBys.isEmpty()) {
            log.warn("[jpa-plus] pageKeyset called without orderBy — cursor-based seek is unreliable without ordering");
            return wrapper;
        }

        // Build: (col_i > v_i) AND (col_0 = v_0 AND ... AND col_{i-1} = v_{i-1})  for each i
        List<Condition> disjuncts = new ArrayList<>();
        for (int i = 0; i < orderBys.size(); i++) {
            OrderBy current = orderBys.get(i);
            Object currentVal = cursor.lastValues().get(current.column().columnName());
            if (currentVal == null) break; // stop if this column has no cursor value

            List<Condition> conjuncts = new ArrayList<>();
            // Equality constraints for all preceding sort columns
            for (int j = 0; j < i; j++) {
                OrderBy preceding = orderBys.get(j);
                Object precedingVal = cursor.lastValues().get(preceding.column().columnName());
                if (precedingVal == null) {
                    // Can't build reliable seek without preceding values — abort this level
                    conjuncts = null;
                    break;
                }
                conjuncts.add(new Eq(preceding.column(), precedingVal));
            }
            if (conjuncts == null) break;

            // Strict comparison for current column
            boolean asc = current.direction() == OrderBy.Direction.ASC;
            conjuncts.add(asc
                    ? new Gt(current.column(), currentVal)
                    : new Lt(current.column(), currentVal));

            disjuncts.add(conjuncts.size() == 1
                    ? conjuncts.getFirst()
                    : new And(conjuncts));
        }

        if (disjuncts.isEmpty()) return wrapper;

        Condition keysetCondition = disjuncts.size() == 1
                ? disjuncts.getFirst()
                : new Or(disjuncts);
        return wrapper.condition(keysetCondition);
    }

    @Override
    public void debug(QueryWrapper<?> wrapper) {
        QueryContext ctx = wrapper.buildContext();
        SqlResult result = sqlCompiler.compile(ctx);
        log.info("==> Debug SQL: {}", result.sql());
        log.info("==> Debug Params: {}", result.params());
    }

    /**
     * Returns a lazy {@link Stream} of results for the given wrapper.
     *
     * <p><b>MUST be consumed inside a {@code try-with-resources} block</b> (or explicitly closed
     * after use) to release the underlying JDBC cursor and connection:</p>
     * <pre>{@code
     * try (Stream<MyEntity> stream = executor.stream(wrapper)) {
     *     stream.forEach(entity -> ...);
     * }
     * }</pre>
     * Failure to close the stream will leak database resources (cursor / connection / Hibernate
     * {@code ScrollableResults}).
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Stream<T> stream(QueryWrapper<T> wrapper) {
        QueryContext ctx = wrapper.buildContext();
        SqlResult result = sqlCompiler.compile(ctx);
        Query query = createNativeQuery(result, wrapper.getEntityClass());
        query.setHint("org.hibernate.fetchSize", streamFetchSize);
        query.setHint("org.hibernate.readOnly", true);
        long start = System.currentTimeMillis();
        return ((Stream<T>) query.getResultStream())
                .onClose(() -> checkSlowSql(result.sql(), System.currentTimeMillis() - start));
    }

    // ─────────── 私有方法 ───────────

    @Override
    public void debug(JoinWrapper<?> wrapper) {
        QueryContext ctx = wrapper.buildContext();
        SqlResult result = sqlCompiler.compile(ctx);
        log.info("==> Debug SQL: {}", result.sql());
        log.info("==> Debug Params: {}", result.params());
    }

    private Query createNativeQuery(SqlResult result, Class<?> resultClass) {
        Query query = entityManager.createNativeQuery(result.sql(), resultClass);
        bindParameters(query, result.params());
        return query;
    }

    private void bindParameters(Query query, Map<String, Object> params) {
        params.forEach(query::setParameter);
    }

    /**
     * 慢 SQL 检测 —— 执行耗时超过阈值时输出 WARN 日志
     *
     * @param sql       执行的 SQL 语句
     * @param elapsedMs 实际执行耗时（毫秒）
     */
    private void checkSlowSql(String sql, long elapsedMs) {
        if (slowSqlThresholdMs > 0 && elapsedMs >= slowSqlThresholdMs) {
            log.warn("[jpa-plus] Slow SQL detected ({}ms ≥ {}ms threshold): {}",
                    elapsedMs, slowSqlThresholdMs, sql);
        }
    }
}
