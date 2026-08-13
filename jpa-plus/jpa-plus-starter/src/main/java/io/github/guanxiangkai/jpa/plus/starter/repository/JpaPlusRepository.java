package io.github.guanxiangkai.jpa.plus.starter.repository;

import io.github.guanxiangkai.jpa.plus.query.pagination.PageResult;
import io.github.guanxiangkai.jpa.plus.query.wrapper.DeleteWrapper;
import io.github.guanxiangkai.jpa.plus.query.wrapper.QueryWrapper;
import io.github.guanxiangkai.jpa.plus.query.wrapper.UpdateWrapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * JPA-Plus 增强 Repository 接口
 *
 * <p>继承 {@link JpaRepository}，扩展 Wrapper 查询能力。
 * 使用者只需继承此接口即可获得 Lambda DSL 查询能力。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * public interface UserRepository extends JpaPlusRepository<User, Long> {}
 *
 * // 查询
 * List<User> users = userRepo.selectList(
 *     QueryWrapper.from(User.class).eq(User::getName, "张三")
 * );
 * }</pre>
 *
 * <p><b>设计模式：</b>适配器模式（Adapter） —— 将 JPA-Plus 查询能力适配到 Spring Data Repository 体系</p>
 *
 * @param <T>  实体类型
 * @param <ID> 主键类型
 * @author guanxiangkai
 * @since 1.0.0
 */
@NoRepositoryBean
public interface JpaPlusRepository<T, ID> extends JpaRepository<T, ID> {

    /**
     * 条件查询列表
     */
    List<T> list(QueryWrapper<T> wrapper);

    /**
     * 条件查询单条
     */
    Optional<T> one(QueryWrapper<T> wrapper);

    /**
     * 条件查询总数
     */
    long count(QueryWrapper<T> wrapper);

    /**
     * 条件分页查询
     */
    PageResult<T> page(QueryWrapper<T> wrapper, Pageable pageable);

    /**
     * 条件更新
     */
    int update(UpdateWrapper<T> wrapper);

    /**
     * 批量条件更新
     */
    int updateBatch(List<UpdateWrapper<T>> wrappers);

    /**
     * 条件删除
     */
    int delete(DeleteWrapper<T> wrapper);

    /**
     * 批量条件删除
     */
    int deleteBatch(List<DeleteWrapper<T>> wrappers);

    /**
     * 批量新增或更新（upsert）
     */
    <S extends T> List<S> upsertBatch(List<S> entities);

    /**
     * 流式查询（大结果集恒定内存）
     *
     * <p>调用方必须在事务中消费并显式关闭 Stream。</p>
     */
    Stream<T> stream(QueryWrapper<T> wrapper);

    /**
     * 调试输出 SQL
     */
    void debug(QueryWrapper<T> wrapper);
}
