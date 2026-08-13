package io.github.guanxiangkai.web.plus.core.model;

import io.github.guanxiangkai.web.plus.core.exception.CoreBizException;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 分页排序构造器
 */
public final class PageSorts {

    private static final Pattern SAFE_SORT_PATH =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

    private PageSorts() {
    }

    public static Sort requestedSort(PageQuery query) {
        if (query == null) {
            return null;
        }
        String sortBy = normalizeSortBy(query.sortBy());
        if (sortBy == null) {
            return null;
        }
        if (!SAFE_SORT_PATH.matcher(sortBy).matches()) {
            throw CoreBizException.invalid("排序字段格式不合法");
        }
        return query.isAsc()
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
    }

    public static Sort resolve(PageQuery query, Sort fallbackSort) {
        Sort requestedSort = requestedSort(query);
        if (requestedSort != null) {
            return requestedSort;
        }
        return fallbackSort != null ? fallbackSort : Sort.unsorted();
    }

    private static String normalizeSortBy(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return null;
        }
        String normalized = sortBy.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
