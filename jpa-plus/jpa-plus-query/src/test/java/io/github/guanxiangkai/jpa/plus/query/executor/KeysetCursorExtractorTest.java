package io.github.guanxiangkai.jpa.plus.query.executor;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;
import io.github.guanxiangkai.jpa.plus.query.context.OrderBy;
import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;
import io.github.guanxiangkai.jpa.plus.query.metadata.TableMeta;
import io.github.guanxiangkai.jpa.plus.query.pagination.KeysetCursor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeysetCursorExtractorTest {

    private final KeysetCursorExtractor extractor = new KeysetCursorExtractor();

    @Test
    void buildNextCursor_readsPrivateInheritedFieldsForEverySortColumn() {
        TableMeta table = TableMeta.of(ChildRow.class);
        List<OrderBy> orderBys = List.of(
                new OrderBy(ColumnMeta.of(table, "created_at", Long.class), OrderBy.Direction.DESC),
                new OrderBy(ColumnMeta.of(table, "id", Long.class), OrderBy.Direction.ASC)
        );

        KeysetCursor cursor = extractor.buildNextCursor(new ChildRow(42L, 7L), orderBys, 20);

        assertThat(cursor.isFirst()).isFalse();
        assertThat(cursor.pageSize()).isEqualTo(20);
        assertThat(cursor.lastValues())
                .containsEntry("created_at", 42L)
                .containsEntry("id", 7L)
                .hasSize(2);
    }

    @Test
    void buildNextCursor_rejectsAnOrderColumnWithoutAMatchingField() {
        TableMeta table = TableMeta.of(ChildRow.class);
        List<OrderBy> orderBys = List.of(
                new OrderBy(ColumnMeta.of(table, "missing_column", Long.class), OrderBy.Direction.ASC)
        );

        assertThatThrownBy(() -> extractor.buildNextCursor(new ChildRow(42L, 7L), orderBys, 20))
                .isInstanceOf(JpaPlusException.class)
                .hasMessageContaining("missingColumn");
    }

    @Test
    void buildNextCursor_rejectsNullSortValues() {
        TableMeta table = TableMeta.of(ChildRow.class);
        List<OrderBy> orderBys = List.of(
                new OrderBy(ColumnMeta.of(table, "created_at", Long.class), OrderBy.Direction.DESC)
        );

        assertThatThrownBy(() -> extractor.buildNextCursor(new ChildRow(null, 7L), orderBys, 20))
                .isInstanceOf(JpaPlusException.class)
                .hasMessageContaining("must be non-null")
                .hasMessageContaining("created_at");
    }

    private static class ParentRow {
        private final Long createdAt;

        private ParentRow(Long createdAt) {
            this.createdAt = createdAt;
        }
    }

    private static final class ChildRow extends ParentRow {
        private final Long id;

        private ChildRow(Long createdAt, Long id) {
            super(createdAt);
            this.id = id;
        }
    }
}
