package io.github.guanxiangkai.jpa.plus.field.dict;

import io.github.guanxiangkai.jpa.plus.core.field.BatchCapableFieldHandler;
import io.github.guanxiangkai.jpa.plus.field.dict.annotation.Dict;
import io.github.guanxiangkai.jpa.plus.field.dict.handler.DictFieldHandler;
import io.github.guanxiangkai.jpa.plus.field.dict.model.DictTranslateItem;
import io.github.guanxiangkai.jpa.plus.field.dict.spi.DictProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DictFieldHandler} 单元测试
 *
 * <p>覆盖：单实体翻译、批量翻译（N+1 查询优化验证）、无标签值跳过、
 * BatchCapableFieldHandler 标记接口声明、标签字段自动推断。</p>
 */
class DictFieldHandlerTest {

    /**
     * 固定字典：gender → [1=男, 2=女, 0=未知]
     */
    private static final List<DictTranslateItem> GENDER_ITEMS = List.of(
            new DictTranslateItem("gender", "1", "男", "primary", 1),
            new DictTranslateItem("gender", "2", "女", "success", 2),
            new DictTranslateItem("gender", "0", "未知", "default", 0)
    );

    AtomicInteger queryCount;
    DictFieldHandler handler;

    @BeforeEach
    void setUp() {
        queryCount = new AtomicInteger();
        DictProvider countingProvider = dictCode -> {
            queryCount.incrementAndGet();
            return GENDER_ITEMS;
        };
        handler = new DictFieldHandler(countingProvider);
    }

    // ─── 标记接口声明 ────────────────────────────────────────────────────

    @Test
    void implementsBatchCapableFieldHandler() {
        assertThat(handler).isInstanceOf(BatchCapableFieldHandler.class);
    }

    // ─── 单实体翻译 ────────────────────────────────────────────────────────

    @Test
    void singleEntity_labelWrittenBack() throws Exception {
        var entity = new GenderEntity();
        entity.gender = "1";
        Field f = GenderEntity.class.getDeclaredField("gender");

        handler.afterQuery(entity, f);
        assertThat(entity.genderLabel).isEqualTo("男");
    }

    @Test
    void singleEntity_noMatchingLabel_labelFieldUnchanged() throws Exception {
        DictProvider emptyProvider = dictCode -> List.of();
        var emptyHandler = new DictFieldHandler(emptyProvider);
        var entity = new GenderEntity();
        entity.gender = "99";
        entity.genderLabel = "original";
        Field f = GenderEntity.class.getDeclaredField("gender");

        emptyHandler.afterQuery(entity, f);
        assertThat(entity.genderLabel).isEqualTo("original");
    }

    @Test
    void singleEntity_nullValue_skipped() throws Exception {
        var entity = new GenderEntity();
        entity.gender = null;
        Field f = GenderEntity.class.getDeclaredField("gender");

        handler.afterQuery(entity, f);
        assertThat(entity.genderLabel).isNull();
        assertThat(queryCount.get()).isZero(); // no query if nothing to translate
    }

    // ─── 批量翻译 — N+1 查询优化 ────────────────────────────────────────────

    @Test
    void batch_onlyOneQueryRegardlessOfEntityCount() throws Exception {
        List<GenderEntity> entities = new ArrayList<>();
        for (String code : List.of("1", "2", "1", "0", "2")) {
            var e = new GenderEntity();
            e.gender = code;
            entities.add(e);
        }
        Field f = GenderEntity.class.getDeclaredField("gender");

        handler.afterQueryBatch(entities, f);

        // Exactly 1 batch query, not N individual queries
        assertThat(queryCount.get()).isEqualTo(1);
        assertThat(entities.get(0).genderLabel).isEqualTo("男");
        assertThat(entities.get(1).genderLabel).isEqualTo("女");
        assertThat(entities.get(2).genderLabel).isEqualTo("男");
        assertThat(entities.get(3).genderLabel).isEqualTo("未知");
        assertThat(entities.get(4).genderLabel).isEqualTo("女");
    }

    @Test
    void batch_distinctValuesDeduped_queryReceivesUniqueValues() throws Exception {
        AtomicInteger providerCallCount = new AtomicInteger();
        DictProvider trackingProvider = dictCode -> {
            providerCallCount.incrementAndGet();
            return List.of(new DictTranslateItem(dictCode, "1", "男", "primary", 1));
        };
        var trackingHandler = new DictFieldHandler(trackingProvider);

        List<GenderEntity> entities = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            var e = new GenderEntity();
            e.gender = "1"; // all same value
            entities.add(e);
        }
        Field f = GenderEntity.class.getDeclaredField("gender");
        trackingHandler.afterQueryBatch(entities, f);

        // Provider called once (batch), not 100 times
        assertThat(providerCallCount.get()).isEqualTo(1);
        // All 100 labels written correctly
        assertThat(entities).allSatisfy(e -> assertThat(e.genderLabel).isEqualTo("男"));
    }

    @Test
    void batch_emptyList_noException() throws Exception {
        Field f = GenderEntity.class.getDeclaredField("gender");
        handler.afterQueryBatch(List.of(), f); // should not throw
        assertThat(queryCount.get()).isZero();
    }

    // ─── 自定义标签字段名 ────────────────────────────────────────────────────

    @Test
    void customLabelField_writtenToCorrectField() throws Exception {
        var entity = new CustomLabelEntity();
        entity.status = "1";
        Field f = CustomLabelEntity.class.getDeclaredField("status");

        handler.afterQuery(entity, f);
        assertThat(entity.statusName).isEqualTo("男"); // provider returns "男" for "1"
    }

    // ─── 缺少标签字段 — 静默跳过 ────────────────────────────────────────────

    @Test
    void missingLabelField_skippedWithoutException() throws Exception {
        var entity = new NoLabelEntity();
        entity.type = "1";
        Field f = NoLabelEntity.class.getDeclaredField("type");

        handler.afterQuery(entity, f); // should not throw
    }

    // ─── 测试实体 ─────────────────────────────────────────────────────────

    static class GenderEntity {
        @Dict(type = "gender")
        String gender;
        String genderLabel;
    }

    static class CustomLabelEntity {
        @Dict(type = "gender", labelField = "statusName")
        String status;
        String statusName;
    }

    static class NoLabelEntity {
        @Dict(type = "type")
        String type;
        // no "typeLabel" field → handler should skip gracefully
    }
}
