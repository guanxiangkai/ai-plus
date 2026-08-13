package io.github.guanxiangkai.jpa.plus.query.executor;

import io.github.guanxiangkai.jpa.plus.query.pagination.KeysetCursor;
import io.github.guanxiangkai.jpa.plus.query.pagination.KeysetPageResult;
import io.github.guanxiangkai.jpa.plus.query.pagination.PageResult;
import io.github.guanxiangkai.jpa.plus.query.wrapper.JoinWrapper;
import io.github.guanxiangkai.jpa.plus.query.wrapper.QueryWrapper;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 只读查询执行器接口
 *
 * <p>提供所有查询操作的统一入口（list / one / count / page / keyset / stream / join）。
 * 写操作（update / delete / upsert）已拆分到 {@link MutationExecutor}，
 * 遵循命令查询职责分离（CQRS）和接口隔离原则（ISP）。</p>
 */
public interface QueryExecutor {

    /**
     * 查询列表
     */
    <T> List<T> list(QueryWrapper<T> wrapper);

    /**
     * 查询单条
     */
    <T> Optional<T> one(QueryWrapper<T> wrapper);

    /**
     * 查询总数
     */
    long count(QueryWrapper<?> wrapper);

    /**
     * 分页查询
     */
    <T> PageResult<T> page(QueryWrapper<T> wrapper, Pageable pageable);

    /**
     * Keyset 深分页查询
     *
     * <p>相比传统 OFFSET 分页，Keyset Pagination 通过将上一页最后一条记录的排序键值转换为
     * {@code WHERE} 条件来定位下一页起点，性能与页码无关（恒定 O(log N)）。</p>
     *
     * <p>要求 {@code wrapper} 必须包含至少一个 {@code orderByAsc/Desc} 排序条件，
     * 且排序字段应具有唯一性或结合主键保证游标精确定位。</p>
     *
     * @param wrapper 查询条件构造器（必须含有排序）
     * @param cursor  上一页游标；首页请传 {@link KeysetCursor#first(int)}
     * @param <T>     实体类型
     * @return 包含当前页数据和下一页游标的 {@link KeysetPageResult}
     */
    <T> KeysetPageResult<T> pageKeyset(QueryWrapper<T> wrapper, KeysetCursor cursor);

    /**
     * Join 查询列表
     */
    <R> List<R> list(JoinWrapper<?> wrapper, Class<R> resultType);

    /**
     * Join 查询单条
     */
    <R> Optional<R> one(JoinWrapper<?> wrapper, Class<R> resultType);

    /**
     * Join 分页查询
     */
    <R> PageResult<R> page(JoinWrapper<?> wrapper, Class<R> resultType, Pageable pageable);

    /**
     * 调试输出 SQL
     */
    void debug(QueryWrapper<?> wrapper);

    /**
     * 流式查询（大结果集恒定内存）
     *
     * <p>调用方必须使用 try-with-resources 显式关闭 Stream，避免连接泄漏。</p>
     */
    <T> Stream<T> stream(QueryWrapper<T> wrapper);

    /**
     * 调试输出 Join SQL
     */
    void debug(JoinWrapper<?> wrapper);
}
