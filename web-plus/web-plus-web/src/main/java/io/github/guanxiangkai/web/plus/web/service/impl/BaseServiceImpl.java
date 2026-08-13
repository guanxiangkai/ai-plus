package io.github.guanxiangkai.web.plus.web.service.impl;

import io.github.guanxiangkai.web.plus.core.converter.EntityConverter;
import io.github.guanxiangkai.web.plus.core.entity.BaseEntity;
import io.github.guanxiangkai.web.plus.core.entity.Enableable;
import io.github.guanxiangkai.web.plus.core.exception.CoreBizException;
import io.github.guanxiangkai.web.plus.core.model.PageQuery;
import io.github.guanxiangkai.web.plus.web.service.IBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 基础 CRUD Service 实现。
 * <p>
 * 继承 {@link ReadOnlyBaseServiceImpl} 复用 Repository 查询、排序、详情映射和字典翻译，并提供
 * 完整 CRUD 所需的写入操作。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public abstract class BaseServiceImpl<Q extends PageQuery, LV, DV, C, U, E extends BaseEntity>
        extends ReadOnlyBaseServiceImpl<Q, LV, DV, E>
        implements IBaseService<Q, LV, DV, C, U, E> {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(C dto) {
        E entity = EntityConverter.toEntity(dto, getRepository().entityClass());
        beforeCreate(entity, dto);
        E saved = getRepository().save(entity);
        afterCreate(saved, dto);
        log.info("[web-plus] 新增 {} id={}", getEntityName(), saved.getId());
        return saved.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, U dto) {
        E entity = requireEntity(id);
        beforeUpdate(entity, dto);
        EntityConverter.updateEntity(dto, entity);
        getRepository().save(entity);
        afterUpdate(entity, dto);
        log.info("[web-plus] 修改 {} id={}", getEntityName(), id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        E entity = requireEntity(id);
        beforeDelete(entity);
        getRepository().delete(entity);
        afterDelete(entity);
        log.info("[web-plus] 删除 {} id={}", getEntityName(), id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        ids.stream().filter(Objects::nonNull).distinct().forEach(this::delete);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(String id, Boolean enabled) {
        if (enabled == null) {
            throw CoreBizException.invalid("启用状态不能为空");
        }
        E entity = requireEntity(id);
        if (!(entity instanceof Enableable enableable)) {
            throw CoreBizException.unsupported(getEntityName() + " 不支持启用状态更新");
        }
        enableable.setEnabled(enabled);
        getRepository().save(entity);
        log.info("[web-plus] 启用状态变更 {} id={} enabled={}", getEntityName(), id, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateEnabled(List<String> ids, Boolean enabled) {
        if (ids == null || ids.isEmpty()) return;
        ids.stream().filter(Objects::nonNull).distinct().forEach(id -> updateEnabled(id, enabled));
    }

    protected void beforeCreate(E entity, C dto) {
    }

    protected void afterCreate(E entity, C dto) {
    }

    protected void beforeUpdate(E entity, U dto) {
    }

    protected void afterUpdate(E entity, U dto) {
    }

    protected void beforeDelete(E entity) {
    }

    protected void afterDelete(E entity) {
    }

}
