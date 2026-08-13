package io.github.guanxiangkai.jpa.plus.query.executor;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;
import io.github.guanxiangkai.jpa.plus.query.compiler.SqlCompiler;
import io.github.guanxiangkai.jpa.plus.query.compiler.SqlResult;
import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;
import io.github.guanxiangkai.jpa.plus.query.wrapper.DefaultDeleteWrapper;
import io.github.guanxiangkai.jpa.plus.query.wrapper.DefaultUpdateWrapper;
import io.github.guanxiangkai.jpa.plus.query.wrapper.DeleteWrapper;
import io.github.guanxiangkai.jpa.plus.query.wrapper.UpdateWrapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * 默认写操作执行器
 *
 * <p>实现 {@link MutationExecutor}，负责 update / delete / upsert 操作。
 * 与 {@link DefaultQueryExecutor} 分离，遵循命令查询职责分离（CQRS）原则：
 * 只读服务仅注入 {@link QueryExecutor}，写服务额外注入 {@link MutationExecutor}。</p>
 *
 * <p>批量 upsert 每 {@value #FLUSH_BATCH_SIZE} 条执行一次 flush + clear，
 * 控制 JPA 一级缓存大小，避免大批量操作导致的 OOM。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class DefaultMutationExecutor implements MutationExecutor {

    private static final int FLUSH_BATCH_SIZE = 200;

    private final EntityManager entityManager;
    private final SqlCompiler sqlCompiler;
    private final long slowSqlThresholdMs;

    public DefaultMutationExecutor(EntityManager entityManager, SqlCompiler sqlCompiler) {
        this(entityManager, sqlCompiler, 0L);
    }

    public DefaultMutationExecutor(EntityManager entityManager,
                                   SqlCompiler sqlCompiler,
                                   long slowSqlThresholdMs) {
        this.entityManager = entityManager;
        this.sqlCompiler = sqlCompiler;
        this.slowSqlThresholdMs = slowSqlThresholdMs;
    }

    @Override
    public int update(UpdateWrapper<?> wrapper) {
        QueryContext ctx = wrapper.buildContext();
        guardFullTableMutation(ctx,
                wrapper instanceof DefaultUpdateWrapper<?> duw && duw.isFullTableMutationAllowed(),
                "UPDATE",
                "modify ALL rows in the table",
                "update all rows");
        return executeMutation(ctx);
    }

    @Override
    public int updateBatch(List<? extends UpdateWrapper<?>> wrappers) {
        return executeBatch(wrappers, this::update);
    }

    @Override
    public int delete(DeleteWrapper<?> wrapper) {
        QueryContext ctx = wrapper.buildContext();
        guardFullTableMutation(ctx,
                wrapper instanceof DefaultDeleteWrapper<?> ddw && ddw.isFullTableMutationAllowed(),
                "DELETE",
                "remove ALL rows from the table",
                "delete all rows");
        return executeMutation(ctx);
    }

    private void guardFullTableMutation(QueryContext ctx,
                                        boolean allowed,
                                        String operation,
                                        String impact,
                                        String intent) {
        if (ctx.runtime().where() != null || allowed) {
            return;
        }
        throw new JpaPlusException(
                "Safety guard: " + operation + " without a WHERE condition would " + impact + ". " +
                        "Add at least one condition via wrapper.where(...), or call " +
                        "wrapper.allowFullTableMutation() to intentionally " + intent + ".");
    }

    private int executeMutation(QueryContext ctx) {
        SqlResult result = sqlCompiler.compile(ctx);
        Query query = entityManager.createNativeQuery(result.sql());
        bindParameters(query, result.params());
        long start = System.currentTimeMillis();
        int affected = query.executeUpdate();
        checkSlowSql(result.sql(), System.currentTimeMillis() - start);
        return affected;
    }

    @Override
    public int deleteBatch(List<? extends DeleteWrapper<?>> wrappers) {
        return executeBatch(wrappers, this::delete);
    }

    private <W> int executeBatch(List<? extends W> wrappers, ToIntFunction<W> executor) {
        if (wrappers == null || wrappers.isEmpty()) return 0;
        int affected = 0;
        for (W wrapper : wrappers) {
            affected += executor.applyAsInt(wrapper);
        }
        return affected;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> upsertBatch(List<T> entities, Class<T> entityClass) {
        if (entities == null || entities.isEmpty()) return List.of();
        List<T> merged = new ArrayList<>(entities.size());
        int processed = 0;
        for (T entity : entities) {
            if (entity == null) continue;
            merged.add(entityManager.merge(entity));
            if (++processed % FLUSH_BATCH_SIZE == 0) {
                // P1-25: Only flush; do NOT call entityManager.clear() here because clear()
                // detaches all previously merged entities, making the returned list stale.
                entityManager.flush();
            }
        }
        entityManager.flush();
        return List.copyOf(merged);
    }

    private void bindParameters(Query query, Map<String, Object> params) {
        params.forEach(query::setParameter);
    }

    private void checkSlowSql(String sql, long elapsedMs) {
        if (slowSqlThresholdMs > 0 && elapsedMs >= slowSqlThresholdMs) {
            log.warn("[jpa-plus] Slow mutation detected ({}ms ≥ {}ms threshold): {}",
                    elapsedMs, slowSqlThresholdMs, sql);
        }
    }
}
