package io.github.guanxiangkai.jpa.plus.query.plan;

import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;
import io.github.guanxiangkai.jpa.plus.query.metadata.TableMeta;
import io.github.guanxiangkai.jpa.plus.query.wrapper.SelectColumn;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MappingPlanCompilerTest {

    @Test
    void compile_reusesCachedPlanWithoutExposingUncheckedCastAtCallSite() {
        TableMeta table = TableMeta.of(UserRow.class);
        List<SelectColumn> columns = List.of(
                new SelectColumn(ColumnMeta.of(table, "user_name", String.class).as("name")),
                new SelectColumn(ColumnMeta.of(table, "age", Integer.class))
        );

        MappingPlan<UserRow> first = MappingPlanCompiler.compile(UserRow.class, columns);
        MappingPlan<UserRow> second = MappingPlanCompiler.compile(UserRow.class, columns);

        assertThat(second).isSameAs(first);
    }

    @Test
    void apply_mapsColumnsThroughPrecompiledSetterHandles() throws Exception {
        TableMeta table = TableMeta.of(UserRow.class);
        List<SelectColumn> columns = List.of(
                new SelectColumn(ColumnMeta.of(table, "user_name", String.class).as("name")),
                new SelectColumn(ColumnMeta.of(table, "age", Integer.class))
        );
        MappingPlan<UserRow> plan = MappingPlanCompiler.compile(UserRow.class, columns);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject(1, String.class)).thenReturn("Alice");
        when(resultSet.getObject(2, int.class)).thenReturn(30);

        UserRow row = plan.apply(resultSet);

        assertThat(row.name).isEqualTo("Alice");
        assertThat(row.age).isEqualTo(30);
    }

    public static class UserRow {
        private String name;
        private int age;

        public void setName(String name) {
            this.name = name;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }
}
