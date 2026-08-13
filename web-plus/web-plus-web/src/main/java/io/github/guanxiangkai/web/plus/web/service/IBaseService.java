package io.github.guanxiangkai.web.plus.web.service;

import io.github.guanxiangkai.web.plus.core.entity.BaseEntity;
import io.github.guanxiangkai.web.plus.core.model.PageQuery;

import java.util.List;

/**
 * 基础 CRUD Service 契约
 * <p>
 * 位于 {@code web-plus-web}，与 {@code BaseServiceImpl} 及
 * {@code BaseController} 统一维护；继承 {@link IReadOnlyService}，使 CRUD 实现可被只读
 * 控制器以查询契约消费。
 * </p>
 *
 * @param <Q>  分页查询 DTO（实现 {@link PageQuery}）
 * @param <LV> 列表 VO
 * @param <DV> 详情 VO
 * @param <C>  创建 DTO
 * @param <U>  更新 DTO
 * @param <E>  实体类型（继承 {@link BaseEntity}）
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface IBaseService<Q extends PageQuery, LV, DV, C, U, E extends BaseEntity>
        extends IReadOnlyService<Q, LV, DV> {

    String create(C dto);

    void update(String id, U dto);

    void delete(String id);

    void batchDelete(List<String> ids);

    void updateEnabled(String id, Boolean enabled);

    void batchUpdateEnabled(List<String> ids, Boolean enabled);
}
