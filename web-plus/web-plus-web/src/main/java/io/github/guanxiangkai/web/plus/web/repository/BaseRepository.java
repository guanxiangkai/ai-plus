package io.github.guanxiangkai.web.plus.web.repository;

import io.github.guanxiangkai.jpa.plus.starter.repository.JpaPlusRepository;
import io.github.guanxiangkai.web.plus.core.converter.EntityConverter;
import io.github.guanxiangkai.web.plus.core.model.PageQuery;
import io.github.guanxiangkai.web.plus.core.model.PageResponse;
import io.github.guanxiangkai.web.plus.core.model.PageSorts;
import org.springframework.core.ResolvableType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

/**
 * 基础 Repository 接口
 * <p>
 * 泛型顺序：{@code BaseRepository<LV, DV, E>}<br>
 * 类型令牌由 Spring {@link ResolvableType} 从子接口声明中自动读取，
 * 调用方无需手动传入 {@code Class} 对象。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public interface UserRepository extends BaseRepository<UserPageVO, UserVO, User> {
 *     Optional<User> findByUsername(String username);
 * }
 * }</pre>
 *
 * @param <LV> 列表 VO 类型
 * @param <DV> 详情 VO 类型
 * @param <E>  实体类型（主键固定为 {@code String}）
 * @author guanxiangkai
 * @since 1.0.0
 */
@NoRepositoryBean
public interface BaseRepository<LV, DV, E>
        extends JpaPlusRepository<E, String>, JpaSpecificationExecutor<E> {

    // ─── 类型令牌（从 UserRepository extends BaseRepository<LV,DV,E> 自动解析）───

    @SuppressWarnings("unchecked")
    default Class<LV> listVoClass() {
        return (Class<LV>) ResolvableType.forClass(getClass()).as(BaseRepository.class).getGeneric(0).resolve();
    }

    @SuppressWarnings("unchecked")
    default Class<DV> detailVoClass() {
        return (Class<DV>) ResolvableType.forClass(getClass()).as(BaseRepository.class).getGeneric(1).resolve();
    }

    @SuppressWarnings("unchecked")
    default Class<E> entityClass() {
        return (Class<E>) ResolvableType.forClass(getClass()).as(BaseRepository.class).getGeneric(2).resolve();
    }

    // ─── VO 查询（内部自动使用已注册的转换器，无需传 Class）───

    /**
     * 按 ID 查询并转换为详情 VO
     */
    default Optional<DV> findVoById(String id) {
        return findById(id).map(e -> EntityConverter.toVo(e, detailVoClass()));
    }

    /**
     * 分页条件查询，转换为列表 VO 的 {@link PageResponse}
     */
    default PageResponse<LV> findPageVo(PageQuery query, Specification<E> spec, Sort fallbackSort) {
        int pageNum = (query != null && query.page() > 0) ? query.page() : 1;
        int pageSize = (query != null && query.size() > 0) ? query.size() : 10;

        Sort sort = PageSorts.resolve(query, fallbackSort);
        Pageable pageable = sort.isSorted()
                ? PageRequest.of(pageNum - 1, pageSize, sort)
                : PageRequest.of(pageNum - 1, pageSize);

        Page<E> page = findAll(spec, pageable);
        return PageResponse.of(
                EntityConverter.toVoList(page.getContent(), listVoClass()),
                page.getTotalElements(), pageNum, pageSize
        );
    }
}
