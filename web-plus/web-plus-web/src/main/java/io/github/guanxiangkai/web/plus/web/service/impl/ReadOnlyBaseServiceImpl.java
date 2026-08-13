package io.github.guanxiangkai.web.plus.web.service.impl;

import io.github.guanxiangkai.web.plus.core.converter.EntityConverter;
import io.github.guanxiangkai.web.plus.core.entity.BaseEntity;
import io.github.guanxiangkai.web.plus.core.entity.Pinnable;
import io.github.guanxiangkai.web.plus.core.entity.Sortable;
import io.github.guanxiangkai.web.plus.core.exception.CoreBizException;
import io.github.guanxiangkai.web.plus.core.model.PageQuery;
import io.github.guanxiangkai.web.plus.core.model.PageResponse;
import io.github.guanxiangkai.web.plus.core.model.PageSorts;
import io.github.guanxiangkai.web.plus.core.spi.ResponseTranslator;
import io.github.guanxiangkai.web.plus.web.repository.BaseRepository;
import io.github.guanxiangkai.web.plus.web.service.IReadOnlyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 基于 Repository 的只读查询 Service 基类。
 * <p>
 * 封装查询条件、排序、分页查询、详情映射、字典翻译和不存在数据的错误语义；只有确实依赖
 * Repository 的读模型才应继承本类。仅由投影或外部数据组成的查询服务可以直接实现
 * {@link IReadOnlyService}。
 * </p>
 *
 * @param <Q>  分页查询 DTO
 * @param <LV> 列表 VO
 * @param <DV> 详情 VO
 * @param <E>  实体类型
 * @author guanxiangkai
 * @since 1.0.0
 */
public abstract class ReadOnlyBaseServiceImpl<Q extends PageQuery, LV, DV, E extends BaseEntity>
        implements IReadOnlyService<Q, LV, DV> {

    private List<ResponseTranslator> responseTranslators = List.of();

    /**
     * 注册响应转换策略链。
     *
     * @param translators 当前容器中的响应转换策略
     */
    @Autowired(required = false)
    void setResponseTranslators(List<ResponseTranslator> translators) {
        responseTranslators = translators.stream()
                .sorted(Comparator.comparingInt(ResponseTranslator::order))
                .toList();
    }

    /**
     * 提供查询与详情映射能力的 Repository。
     *
     * @return 当前读模型的 Repository
     */
    protected abstract BaseRepository<LV, DV, E> getRepository();

    /**
     * 构造查询条件，默认返回恒真条件。
     *
     * @param query 查询条件；全量查询时可为 {@code null}
     * @return JPA 查询条件
     */
    protected Specification<E> buildQuerySpec(Q query) {
        return (root, criteriaQuery, cb) -> cb.conjunction();
    }

    /**
     * 构造排序规则；显式请求排序优先于实体默认排序。
     *
     * @param query 查询条件；全量查询时可为 {@code null}
     * @return 排序规则
     */
    protected Sort buildSort(Q query) {
        Class<E> entityClass = getRepository().entityClass();
        Sort requestedSort = PageSorts.requestedSort(query);
        if (requestedSort != null) {
            return resolveComponentSort(entityClass, requestedSort);
        }
        if (Sortable.class.isAssignableFrom(entityClass)) {
            return Sort.by(Sort.Order.asc("sortInfo.sortOrder"), Sort.Order.desc("createTime"));
        }
        return Sort.by(Sort.Order.desc("createTime"));
    }

    /**
     * 获取实体名称，用于统一的不存在数据错误信息。
     *
     * @return 实体简单类名
     */
    protected String getEntityName() {
        return getRepository().entityClass().getSimpleName();
    }

    @Override
    public PageResponse<LV> list(Q query) {
        PageResponse<LV> page = getRepository().findPageVo(query, buildQuerySpec(query), buildSort(query));
        translateList(page.records());
        return page;
    }

    @Override
    public DV detail(String id) {
        return translate(EntityConverter.toVo(requireEntity(id), getRepository().detailVoClass()));
    }

    /**
     * 翻译单个 VO 的字典字段。
     *
     * @param value 待翻译对象
     * @param <T> 对象类型
     * @return 翻译后的对象
     */
    protected <T> T translate(T value) {
        T translated = value;
        for (ResponseTranslator translator : responseTranslators) {
            translated = translator.translate(translated);
        }
        return translated;
    }

    /**
     * 翻译 VO 列表的字典字段。
     *
     * @param values 待翻译对象列表
     * @param <T> 对象类型
     * @return 翻译后的对象列表
     */
    protected <T> List<T> translateList(List<T> values) {
        List<T> translated = values;
        for (ResponseTranslator translator : responseTranslators) {
            translated = translator.translateList(translated);
        }
        return translated;
    }

    /**
     * 获取指定实体，不存在时返回统一业务错误。
     *
     * @param id 实体标识
     * @return 已存在实体
     */
    protected E requireEntity(String id) {
        return getRepository().findById(id)
                .orElseThrow(() -> CoreBizException.notFound(getEntityName(), id));
    }

    private Sort resolveComponentSort(Class<E> entityClass, Sort sort) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            orders.add(order.withProperty(resolveComponentSortProperty(entityClass, order.getProperty())));
        }
        return Sort.by(orders);
    }

    private String resolveComponentSortProperty(Class<E> entityClass, String property) {
        if (Sortable.class.isAssignableFrom(entityClass) && "sortOrder".equals(property)) {
            return "sortInfo.sortOrder";
        }
        if (Pinnable.class.isAssignableFrom(entityClass) && "pinned".equals(property)) {
            return "pinInfo.pinned";
        }
        return property;
    }
}
