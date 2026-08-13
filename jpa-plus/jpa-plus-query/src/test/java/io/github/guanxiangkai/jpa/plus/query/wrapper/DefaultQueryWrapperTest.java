package io.github.guanxiangkai.jpa.plus.query.wrapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultQueryWrapperTest {

    @Test
    void limit_returnsCopy_doesNotMutateOriginal() {
        QueryWrapper<TestEntity> original = QueryWrapper.from(TestEntity.class)
                .eq(TestEntity::getName, "test");
        QueryWrapper<TestEntity> limited = original.limit(0, 1);

        // Original should be untouched (no limit set → offset/rows are null)
        var origCtx = original.buildContext();
        assertThat(origCtx.runtime().offset()).isNull();
        assertThat(origCtx.runtime().rows()).isNull();

        // Limited copy should have limit applied
        var limitCtx = limited.buildContext();
        assertThat(limitCtx.runtime().offset()).isZero();
        assertThat(limitCtx.runtime().rows()).isEqualTo(1);
    }

    @Test
    void chaining_multipleConditions() {
        QueryWrapper<TestEntity> w = QueryWrapper.from(TestEntity.class)
                .eq(TestEntity::getStatus, 1)
                .ne(TestEntity::isDeleted, true)
                .orderByAsc(TestEntity::getCreateTime);
        var ctx = w.buildContext();
        assertThat(ctx.runtime().orderBys()).hasSize(1);
    }

    @Test
    void limit_multipleTimes_lastWins() {
        QueryWrapper<TestEntity> w = QueryWrapper.from(TestEntity.class);
        QueryWrapper<TestEntity> a = w.limit(0, 10);
        QueryWrapper<TestEntity> b = a.limit(5, 20);
        var ctx = b.buildContext();
        assertThat(ctx.runtime().offset()).isEqualTo(5);
        assertThat(ctx.runtime().rows()).isEqualTo(20);
        // a should be unchanged
        var aCtx = a.buildContext();
        assertThat(aCtx.runtime().offset()).isZero();
        assertThat(aCtx.runtime().rows()).isEqualTo(10);
    }

    // Simple POJO test entity — no JPA annotations needed; TableMeta falls back to snake_case
    static class TestEntity {
        private String name;
        private boolean deleted;
        private String status;
        private String createTime;

        public String getName() {
            return name;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public String getStatus() {
            return status;
        }

        public String getCreateTime() {
            return createTime;
        }
    }
}
