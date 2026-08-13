package io.github.guanxiangkai.jpa.plus.starter.repository;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;
import io.github.guanxiangkai.jpa.plus.core.executor.JpaPlusExecutor;
import io.github.guanxiangkai.jpa.plus.core.model.QueryInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.SaveInvocation;
import io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.annotation.LogicDelete;
import io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.handler.LogicDeleteFieldHandler;
import io.github.guanxiangkai.jpa.plus.query.executor.MutationExecutor;
import io.github.guanxiangkai.jpa.plus.query.executor.QueryExecutor;
import io.github.guanxiangkai.jpa.plus.query.pagination.PageResult;
import io.github.guanxiangkai.jpa.plus.query.wrapper.DeleteWrapper;
import io.github.guanxiangkai.jpa.plus.query.wrapper.QueryWrapper;
import io.github.guanxiangkai.jpa.plus.query.wrapper.UpdateWrapper;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.DeleteSpecification;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * JPA Plus 的唯一 Repository 基类实现。
 *
 * <p>所有 Spring Data 标准写入入口都必须经过 {@link SaveInvocation}，从而保证 ID、
 * 自动填充、租户和审计链拥有一致的生命周期。含 {@link LogicDelete} 的实体通过更新删除标记
 * 实现逻辑删除；未声明该注解的实体继续遵循 Spring Data JPA 的物理删除语义。它在保留
 * {@link SimpleJpaRepository} 原生契约的同时，委托 {@link JpaPlusExecutor} 和
 * {@link QueryExecutor} 执行增强生命周期与查询。</p>
 */
public class SimpleJpaPlusRepository<T, ID>
        extends SimpleJpaRepository<T, ID>
        implements JpaPlusRepository<T, ID> {

    private final JpaPlusExecutor executor;
    private final QueryExecutor queryExecutor;
    private final MutationExecutor mutationExecutor;
    private final JpaEntityInformation<T, ?> entityInformation;

    public SimpleJpaPlusRepository(JpaEntityInformation<T, ?> entityInformation,
                                   EntityManager entityManager,
                                   JpaPlusExecutor executor,
                                   QueryExecutor queryExecutor,
                                   MutationExecutor mutationExecutor) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.executor = executor;
        this.queryExecutor = queryExecutor;
        this.mutationExecutor = mutationExecutor;
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public <S extends T> S save(S entity) {
        try {
            return (S) executor.execute(new SaveInvocation(entityInformation.getJavaType(), entity));
        } catch (Throwable e) {
            throw new JpaPlusException("保存实体时执行 JPA Plus 生命周期失败", e);
        }
    }

    @Override
    @Transactional
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        List<S> saved = new ArrayList<>();
        for (S entity : entities) {
            saved.add(save(entity));
        }
        return saved;
    }

    @Override
    @Transactional
    public <S extends T> S saveAndFlush(S entity) {
        S saved = save(entity);
        flush();
        return saved;
    }

    @Override
    @Transactional
    public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) {
        List<S> saved = saveAll(entities);
        flush();
        return saved;
    }

    @Override
    @Transactional
    public void deleteById(ID id) {
        delete(findById(id).orElseThrow());
    }

    @Override
    @Transactional
    public void delete(T entity) {
        Field logicDeleteField = findLogicDeleteField();
        if (logicDeleteField == null) {
            super.delete(entity);
            return;
        }
        markDeleted(entity, logicDeleteField);
        save(entity);
    }

    @Override
    @Transactional
    public void deleteAllById(Iterable<? extends ID> ids) {
        for (ID id : ids) {
            deleteById(id);
        }
    }

    @Override
    @Transactional
    public void deleteAll(Iterable<? extends T> entities) {
        for (T entity : entities) {
            delete(entity);
        }
    }

    @Override
    @Transactional
    public void deleteAll() {
        deleteAll(findAll());
    }

    @Override
    @Transactional
    public void deleteAllInBatch(Iterable<T> entities) {
        if (hasLogicDeleteField()) {
            deleteAll(entities);
            return;
        }
        super.deleteAllInBatch(entities);
    }

    @Override
    @Transactional
    public void deleteAllByIdInBatch(Iterable<ID> ids) {
        if (hasLogicDeleteField()) {
            deleteAllById(ids);
            return;
        }
        super.deleteAllByIdInBatch(ids);
    }

    @Override
    @Transactional
    public void deleteAllInBatch() {
        if (hasLogicDeleteField()) {
            deleteAll();
            return;
        }
        super.deleteAllInBatch();
    }

    @Override
    @Transactional
    public long delete(DeleteSpecification<T> specification) {
        rejectBulkPhysicalDeleteForLogicEntity();
        return super.delete(specification);
    }

    @Override
    public Optional<T> findById(ID id) {
        return super.findById(id).filter(this::isVisible);
    }

    @Override
    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    @Override
    public List<T> findAll() {
        return hasLogicDeleteField()
                ? super.findAll(notDeletedSpecification())
                : super.findAll();
    }

    @Override
    public List<T> findAll(Sort sort) {
        return hasLogicDeleteField()
                ? super.findAll(notDeletedSpecification(), sort)
                : super.findAll(sort);
    }

    @Override
    public List<T> findAllById(Iterable<ID> ids) {
        return visible(super.findAllById(ids));
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        if (!hasLogicDeleteField()) {
            return super.findAll(pageable);
        }
        return super.findAll(notDeletedSpecification(), pageable);
    }

    @Override
    public long count() {
        return hasLogicDeleteField() ? super.count(notDeletedSpecification()) : super.count();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> list(QueryWrapper<T> wrapper) {
        try {
            var invocation = new QueryInvocation(
                    entityInformation.getJavaType(),
                    wrapper.buildContext()
            );
            return (List<T>) executor.execute(invocation);
        } catch (Throwable e) {
            throw new JpaPlusException("Query execution failed", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<T> one(QueryWrapper<T> wrapper) {
        List<T> results = list(wrapper.limit(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public long count(QueryWrapper<T> wrapper) {
        return queryExecutor.count(wrapper);
    }

    @Override
    public PageResult<T> page(QueryWrapper<T> wrapper, Pageable pageable) {
        return queryExecutor.page(wrapper, pageable);
    }

    @Override
    @Transactional
    public int update(UpdateWrapper<T> wrapper) {
        return mutationExecutor.update(wrapper);
    }

    @Override
    @Transactional
    public int updateBatch(List<UpdateWrapper<T>> wrappers) {
        return mutationExecutor.updateBatch(wrappers);
    }

    @Override
    @Transactional
    public int delete(DeleteWrapper<T> wrapper) {
        rejectBulkPhysicalDeleteForLogicEntity();
        return mutationExecutor.delete(wrapper);
    }

    @Override
    @Transactional
    public int deleteBatch(List<DeleteWrapper<T>> wrappers) {
        rejectBulkPhysicalDeleteForLogicEntity();
        return mutationExecutor.deleteBatch(wrappers);
    }

    @Override
    @SuppressWarnings("unchecked")
    @Transactional
    public <S extends T> List<S> upsertBatch(List<S> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return (List<S>) mutationExecutor.upsertBatch((List<T>) entities, entityInformation.getJavaType());
    }

    @Override
    public Stream<T> stream(QueryWrapper<T> wrapper) {
        return queryExecutor.stream(wrapper);
    }

    @Override
    public void debug(QueryWrapper<T> wrapper) {
        queryExecutor.debug(wrapper);
    }

    private List<T> visible(List<T> entities) {
        if (!hasLogicDeleteField()) {
            return entities;
        }
        return entities.stream().filter(this::isVisible).toList();
    }

    private boolean isVisible(T entity) {
        Field field = findLogicDeleteField();
        if (field == null) {
            return true;
        }
        ReflectionUtils.makeAccessible(field);
        LogicDelete annotation = field.getAnnotation(LogicDelete.class);
        Object expected = LogicDeleteFieldHandler.resolveNotDeletedValue(field.getType(), annotation);
        Object actual = ReflectionUtils.getField(field, entity);
        return expected.equals(actual);
    }

    private boolean hasLogicDeleteField() {
        return findLogicDeleteField() != null;
    }

    /**
     * 构造逻辑删除实体的未删除查询谓词。
     *
     * <p>该谓词交由 Spring Data JPA 的标准分页查询执行，使排序、总数统计以及 Hibernate
     * 租户过滤和数据权限边界都在数据库侧完成。</p>
     *
     * @return 仅匹配未删除实体的查询条件
     */
    private Specification<T> notDeletedSpecification() {
        Field field = findLogicDeleteField();
        if (field == null) {
            throw new IllegalStateException("未声明 @LogicDelete 的实体不能构造逻辑删除查询条件");
        }
        LogicDelete annotation = field.getAnnotation(LogicDelete.class);
        Object expected = LogicDeleteFieldHandler.resolveNotDeletedValue(field.getType(), annotation);
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field.getName()), expected);
    }

    private void rejectBulkPhysicalDeleteForLogicEntity() {
        if (hasLogicDeleteField()) {
            throw new UnsupportedOperationException(
                    "带 @LogicDelete 的实体不允许批量物理删除；请先查询实体后调用 delete/deleteAll，以保证逻辑删除与字段审计链生效。");
        }
    }

    private void markDeleted(T entity, Field field) {
        ReflectionUtils.makeAccessible(field);
        LogicDelete annotation = field.getAnnotation(LogicDelete.class);
        ReflectionUtils.setField(field, entity,
                LogicDeleteFieldHandler.resolveDeletedValue(field.getType(), annotation));
    }

    private Field findLogicDeleteField() {
        Class<?> type = entityInformation.getJavaType();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(LogicDelete.class)) {
                    return field;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
