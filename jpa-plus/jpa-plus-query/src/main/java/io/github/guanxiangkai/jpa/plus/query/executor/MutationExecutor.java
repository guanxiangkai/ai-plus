package io.github.guanxiangkai.jpa.plus.query.executor;

import io.github.guanxiangkai.jpa.plus.query.wrapper.DeleteWrapper;
import io.github.guanxiangkai.jpa.plus.query.wrapper.UpdateWrapper;

import java.util.List;

/**
 * 写操作执行器接口
 *
 * <p>将变更操作（update / delete / upsert）从 {@link QueryExecutor} 中拆分出来，
 * 遵循接口隔离原则（ISP）：只读场景注入 {@link QueryExecutor}，
 * 需要写操作的场景额外注入 {@link MutationExecutor}。</p>
 *
 * <p><b>设计原则：</b>命令查询职责分离（CQRS）</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface MutationExecutor {

    /**
     * 执行单条 UPDATE
     */
    int update(UpdateWrapper<?> wrapper);

    /**
     * 批量执行 UPDATE（逐条，在同一事务中）
     */
    int updateBatch(List<? extends UpdateWrapper<?>> wrappers);

    /**
     * 执行单条 DELETE
     */
    int delete(DeleteWrapper<?> wrapper);

    /**
     * 批量执行 DELETE（逐条，在同一事务中）
     */
    int deleteBatch(List<? extends DeleteWrapper<?>> wrappers);

    /**
     * 批量新增或更新（upsert）
     *
     * <p>通过 {@code EntityManager.merge()} 实现。每 {@code flushBatchSize} 条
     * 自动 flush + clear，控制一级缓存大小。</p>
     */
    <T> List<T> upsertBatch(List<T> entities, Class<T> entityClass);
}
