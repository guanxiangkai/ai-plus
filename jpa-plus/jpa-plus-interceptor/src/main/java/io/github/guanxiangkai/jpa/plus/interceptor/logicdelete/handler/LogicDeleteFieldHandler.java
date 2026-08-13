package io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.handler;

import io.github.guanxiangkai.jpa.plus.core.field.FieldHandler;
import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.annotation.LogicDelete;
import io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.enums.LogicDeleteValue;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;

/**
 * 逻辑删除字段处理器
 *
 * <p>保存时若逻辑删除字段为 null，根据字段类型自动设为"未删除"默认值。</p>
 *
 * <p><b>类型推导规则：</b>
 * <ul>
 *   <li>{@code Integer/int} → 0</li>
 *   <li>{@code Boolean/boolean} → false</li>
 *   <li>{@code Long/long} → 0L</li>
 *   <li>{@code String} → 注解的 {@code defaultValue()}</li>
 * </ul>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class LogicDeleteFieldHandler implements FieldHandler {

    /**
     * 根据字段类型推导"未删除"值
     */
    public static Object resolveNotDeletedValue(Class<?> fieldType, LogicDelete annotation) {
        // customType 优先
        if (annotation.customType() != LogicDeleteValue.class) {
            return ReflectionUtils.instantiate(annotation.customType()).notDeletedValue();
        }
        // 按字段类型自动推导
        return resolveByFieldType(fieldType, annotation.defaultValue());
    }

    /**
     * 根据字段类型推导"已删除"值
     */
    public static Object resolveDeletedValue(Class<?> fieldType, LogicDelete annotation) {
        if (annotation.customType() != LogicDeleteValue.class) {
            return ReflectionUtils.instantiate(annotation.customType()).deletedValue();
        }
        return resolveByFieldType(fieldType, annotation.value());
    }

    /**
     * 按字段 Java 类型自动推导值
     */
    private static Object resolveByFieldType(Class<?> fieldType, String annotationValue) {
        return switch (fieldType.getName()) {
            case "int", "java.lang.Integer" -> Integer.parseInt(annotationValue);
            case "long", "java.lang.Long" -> Long.parseLong(annotationValue);
            case "boolean", "java.lang.Boolean" ->
                    "1".equals(annotationValue) || "true".equalsIgnoreCase(annotationValue);
            case "short", "java.lang.Short" -> Short.parseShort(annotationValue);
            case "byte", "java.lang.Byte" -> Byte.parseByte(annotationValue);
            default -> annotationValue; // String 及其他类型直接使用注解值
        };
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public boolean supports(Field field) {
        return field.isAnnotationPresent(LogicDelete.class);
    }

    @Override
    public void beforeSave(Object entity, Field field) {
        try {
            Object value = ReflectionUtils.getFieldValue(entity, field);
            if (value == null) {
                LogicDelete annotation = field.getAnnotation(LogicDelete.class);
                Object defaultVal = resolveNotDeletedValue(field.getType(), annotation);
                ReflectionUtils.setFieldValue(entity, field, defaultVal);
            }
        } catch (Exception e) {
            log.error("逻辑删除字段处理失败: field={}", field.getName(), e);
        }
    }
}
