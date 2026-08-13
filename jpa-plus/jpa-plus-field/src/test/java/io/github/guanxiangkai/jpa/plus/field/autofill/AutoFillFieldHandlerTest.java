package io.github.guanxiangkai.jpa.plus.field.autofill;

import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.CreateBy;
import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.CreateTime;
import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.UpdateBy;
import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.UpdateTime;
import io.github.guanxiangkai.jpa.plus.field.autofill.handler.AutoFillFieldHandler;
import io.github.guanxiangkai.jpa.plus.field.autofill.spi.CurrentUserProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AutoFillFieldHandlerTest {

    static final CurrentUserProvider USER_PROVIDER = () -> "user123";

    @Test
    void createTime_filledOnlyWhenNull() throws Exception {
        var handler = new AutoFillFieldHandler(USER_PROVIDER);
        var entity = new TestEntity();
        Field f = TestEntity.class.getDeclaredField("createTime");
        handler.beforeSave(entity, f);
        assertThat(entity.createTime).isNotNull();

        // second call: should NOT overwrite
        LocalDateTime first = entity.createTime;
        Thread.sleep(5);
        handler.beforeSave(entity, f);
        assertThat(entity.createTime).isEqualTo(first);
    }

    @Test
    void updateTime_alwaysOverwritten() throws Exception {
        var handler = new AutoFillFieldHandler(USER_PROVIDER);
        var entity = new TestEntity();
        Field f = TestEntity.class.getDeclaredField("updateTime");
        handler.beforeSave(entity, f);
        LocalDateTime first = entity.updateTime;
        Thread.sleep(5);
        handler.beforeSave(entity, f);
        assertThat(entity.updateTime).isAfterOrEqualTo(first);
    }

    @Test
    void createBy_filledOnlyWhenNull() throws Exception {
        var handler = new AutoFillFieldHandler(USER_PROVIDER);
        var entity = new TestEntity();
        Field f = TestEntity.class.getDeclaredField("createBy");
        handler.beforeSave(entity, f);
        assertThat(entity.createBy).isEqualTo("user123");

        entity.createBy = "originalUser";
        handler.beforeSave(entity, f);
        assertThat(entity.createBy).isEqualTo("originalUser"); // unchanged
    }

    @Test
    void updateBy_alwaysOverwritten() throws Exception {
        var handler = new AutoFillFieldHandler(USER_PROVIDER);
        var entity = new TestEntity();
        Field f = TestEntity.class.getDeclaredField("updateBy");
        handler.beforeSave(entity, f);
        assertThat(entity.updateBy).isEqualTo("user123");
    }

    @Test
    void noUserProvider_createBySkipped() throws Exception {
        var handler = new AutoFillFieldHandler(null);
        var entity = new TestEntity();
        Field f = TestEntity.class.getDeclaredField("createBy");
        handler.beforeSave(entity, f); // should not throw
        assertThat(entity.createBy).isNull();
    }

    static class TestEntity {
        @CreateTime
        LocalDateTime createTime;
        @UpdateTime
        LocalDateTime updateTime;
        @CreateBy
        String createBy;
        @UpdateBy
        String updateBy;
    }
}
