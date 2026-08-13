package io.github.guanxiangkai.jpa.plus.field.dict.handler;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;
import io.github.guanxiangkai.jpa.plus.core.field.BatchCapableFieldHandler;
import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.field.dict.annotation.Dict;
import io.github.guanxiangkai.jpa.plus.field.dict.spi.DictProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 字典回写处理器
 *
 * <p><b>设计模式：</b>策略模式 —— 通过 {@link DictProvider} SPI 解耦字典数据获取</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class DictFieldHandler implements BatchCapableFieldHandler {

    /**
     * 字典数据提供者（由用户通过 SPI 或 Spring Bean 注入）
     */
    private final DictProvider dictProvider;

    @Override
    public int order() {
        return 200;
    }

    @Override
    public boolean supports(Field field) {
        return field.isAnnotationPresent(Dict.class);
    }

    @Override
    public void beforeSaveBatch(List<?> entities, Field field) {
        // 字典处理仅在查询后回写标签，保存前无需操作
    }

    @Override
    public void afterQuery(Object entity, Field field) {
        if (entity == null) return;
        try {
            Dict dict = field.getAnnotation(Dict.class);
            if (dict == null) return;

            String labelFieldName = dict.labelField().isEmpty()
                    ? field.getName() + "Label"
                    : dict.labelField();
            Field labelField = ReflectionUtils.findField(entity.getClass(), labelFieldName);
            if (labelField == null) {
                log.warn("字典标签字段不存在，已跳过回写: entity={}, sourceField={}, labelField={}",
                        entity.getClass().getSimpleName(), field.getName(), labelFieldName);
                return;
            }

            Object dictValue = ReflectionUtils.getFieldValue(entity, field);
            if (dictValue == null) return;

            dictProvider.getLabel(dict.type(), dictValue)
                    .ifPresent(label -> ReflectionUtils.setFieldValue(entity, labelField, label));
        } catch (Exception e) {
            throw new JpaPlusException("字典处理异常: field=" + field.getName(), e);
        }
    }

    @Override
    public void afterQueryBatch(List<?> entities, Field field) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        try {
            Dict dict = field.getAnnotation(Dict.class);
            if (dict == null) {
                return;
            }

            String labelFieldName = dict.labelField().isEmpty()
                    ? field.getName() + "Label"
                    : dict.labelField();
            // 批量结果可包含空元素，以第一个非空实例确定标签字段。
            Object representative = entities.stream()
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (representative == null) {
                return;
            }
            Field labelField = ReflectionUtils.findField(representative.getClass(), labelFieldName);
            if (labelField == null) {
                log.warn("字典标签字段不存在，已跳过回写: entity={}, sourceField={}, labelField={}",
                        representative.getClass().getSimpleName(), field.getName(), labelFieldName);
                return;
            }

            Set<String> distinctValues = new HashSet<>();
            List<EntityValuePair> valuePairs = new ArrayList<>(entities.size());
            for (Object entity : entities) {
                Object dictValue = ReflectionUtils.getFieldValue(entity, field);
                if (dictValue == null) {
                    continue;
                }
                String value = String.valueOf(dictValue);
                distinctValues.add(value);
                valuePairs.add(new EntityValuePair(entity, value));
            }
            if (distinctValues.isEmpty()) {
                return;
            }

            // 批量翻译：避免逐条调用导致 N 次外部查询
            Map<String, String> labels = dictProvider.getLabels(dict.type(), distinctValues);
            for (EntityValuePair pair : valuePairs) {
                String label = labels.get(pair.value());
                if (label != null) {
                    ReflectionUtils.setFieldValue(pair.entity(), labelField, label);
                }
            }
        } catch (Exception e) {
            throw new JpaPlusException("字典处理异常: field=" + field.getName(), e);
        }
    }

    private record EntityValuePair(Object entity, String value) {
    }
}
