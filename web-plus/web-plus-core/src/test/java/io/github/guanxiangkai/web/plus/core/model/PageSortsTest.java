package io.github.guanxiangkai.web.plus.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageSortsTest {

    @Test
    void acceptsSafeNestedSortPath() {
        assertThatCode(() -> PageSorts.requestedSort(
                new PageRequest(1, 10, "creator.name", "ASC", null)
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsafeSortExpression() {
        assertThatThrownBy(() -> PageSorts.requestedSort(
                new PageRequest(1, 10, "createTime desc;drop table user", "DESC", null)
        )).hasMessageContaining("排序字段格式不合法");
    }
}
