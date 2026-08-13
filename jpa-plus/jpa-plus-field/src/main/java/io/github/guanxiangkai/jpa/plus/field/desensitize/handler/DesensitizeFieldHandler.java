package io.github.guanxiangkai.jpa.plus.field.desensitize.handler;

import io.github.guanxiangkai.jpa.plus.core.field.FieldHandler;
import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.field.desensitize.annotation.Desensitize;
import io.github.guanxiangkai.jpa.plus.field.desensitize.spi.MaskStrategy;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;

/**
 * 字段脱敏处理器
 *
 * <p>实现 {@link FieldHandler} 接口，对标注了 {@link Desensitize} 的字段
 * 在查询后执行掩码处理。优先使用 {@code customStrategy}，否则使用内置 {@code strategy}。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class DesensitizeFieldHandler implements FieldHandler {

    private static final ClassValue<MaskStrategy> CUSTOM_STRATEGY_CACHE = new ClassValue<>() {
        @Override
        protected MaskStrategy computeValue(Class<?> type) {
            return ReflectionUtils.instantiate(type.asSubclass(MaskStrategy.class));
        }
    };

    @Override
    public int order() {
        return 300;
    }

    @Override
    public boolean supports(Field field) {
        return field.isAnnotationPresent(Desensitize.class);
    }

    @Override
    public void afterQuery(Object entity, Field field) {
        String originalValue = null;
        try {
            Object value = ReflectionUtils.getFieldValue(entity, field);
            if (value instanceof String str && !str.isEmpty()) {
                originalValue = str;
                Desensitize annotation = field.getAnnotation(Desensitize.class);
                MaskStrategy strategy = resolveStrategy(annotation);
                String masked = strategy.mask(str, annotation.maskChar());
                ReflectionUtils.setFieldValue(entity, field, masked);
            }
        } catch (Exception e) {
            log.error("字段脱敏失败: field={}", field.getName(), e);
            // P0-15: On failure, mask the field with a sentinel value to prevent PII exposure.
            // Never allow the raw value to be returned when masking fails.
            try {
                if (originalValue != null) {
                    ReflectionUtils.setFieldValue(entity, field, "***");
                }
            } catch (Exception masked) {
                log.error("字段脱敏兜底替换也失败: field={}", field.getName(), masked);
            }
        }
    }

    /**
     * 解析脱敏策略：customStrategy 优先于 strategy
     */
    private MaskStrategy resolveStrategy(Desensitize annotation) {
        Class<? extends MaskStrategy> customClass = annotation.customStrategy();
        if (customClass != MaskStrategy.class) {
            return CUSTOM_STRATEGY_CACHE.get(customClass);
        }
        // 内置枚举本身就是 MaskStrategy
        return annotation.strategy();
    }
}
