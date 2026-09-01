package io.github.guanxiangkai.web.plus.web.service;

import io.github.guanxiangkai.web.plus.core.model.PageQuery;
import io.github.guanxiangkai.web.plus.core.model.PageResponse;

/**
 * 只读查询 Service 契约。
 * <p>
 * 用于仅提供分页列表和详情查询的业务投影。实现类不需要伪造实体、Repository
 * 或写入命令，即可复用 {@code ReadOnlyBaseController} 的统一 HTTP、权限与审计契约。
 * </p>
 *
 * @param <Q>  分页查询 DTO
 * @param <LV> 列表 VO
 * @param <DV> 详情 VO
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface IReadOnlyService<Q extends PageQuery, LV, DV> {

    /**
     * 分页查询列表数据。
     *
     * @param query 分页、排序及业务筛选条件
     * @return 分页列表结果
     */
    PageResponse<LV> list(Q query);

    /**
     * 根据标识查询详情。
     *
     * @param id 数据标识
     * @return 详情数据
     */
    DV detail(String id);
}
