package io.github.guanxiangkai.web.plus.core.entity;

/**
 * 排序能力接口
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface Sortable {

    SortInfo getSortInfo();

    void setSortInfo(SortInfo sortInfo);

    default Integer getSortOrder() {
        SortInfo sortInfo = getSortInfo();
        return sortInfo == null ? null : sortInfo.getSortOrder();
    }

    default void setSortOrder(Integer sortOrder) {
        SortInfo sortInfo = getSortInfo();
        if (sortInfo == null) {
            sortInfo = new SortInfo();
            setSortInfo(sortInfo);
        }
        sortInfo.setSortOrder(sortOrder);
    }
}
