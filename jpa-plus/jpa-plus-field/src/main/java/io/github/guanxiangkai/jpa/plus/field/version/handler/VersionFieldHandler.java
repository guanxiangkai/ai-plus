package io.github.guanxiangkai.jpa.plus.field.version.handler;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;
import io.github.guanxiangkai.jpa.plus.core.field.FieldHandler;
import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.field.version.annotation.Version;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;

/**
 * 乐观锁版本自增处理器
 *
 * <p>实现 {@link FieldHandler}，在保存前自动将 {@link Version} 标注的字段值 +1。
 * 根据字段类型自动处理：
 * <ul>
 *   <li>{@code Integer/int} → null 时初始化为 0，否则 +1</li>
 *   <li>{@code Long/long} → null 时初始化为 0L，否则 +1L</li>
 *   <li>{@code Short/short} → null 时初始化为 0，否则 +1</li>
 * </ul>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class VersionFieldHandler implements FieldHandler {

    @Override
    public int order() {
        return 400;
    }

    @Override
    public boolean supports(Field field) {
        return field.isAnnotationPresent(Version.class);
    }

    @Override
    public void beforeSave(Object entity, Field field) {
        try {
            Object value = ReflectionUtils.getFieldValue(entity, field);
            if (value == null) {
                Object initial = resolveInitialVersion(field.getType());
                ReflectionUtils.setFieldValue(entity, field, initial);
            } else {
                // P1-13: Reset to 0 on overflow rather than wrapping to negative, which would
                // cause false concurrent-modification errors and invisible version regressions.
                Object incremented = switch (value) {
                    case Integer v -> v == Integer.MAX_VALUE ? 0 : v + 1;
                    case Long v -> v == Long.MAX_VALUE ? 0L : v + 1L;
                    case Short v -> v == Short.MAX_VALUE ? (short) 0 : (short) (v + 1);
                    default -> value;
                };
                ReflectionUtils.setFieldValue(entity, field, incremented);
            }
        } catch (Exception e) {
            // P1-12: Do NOT swallow this exception. A silent failure leaves the version
            // field unchanged, which silently breaks the optimistic locking contract.
            throw new JpaPlusException("版本号处理失败: field=" + field.getName(), e);
        }
    }

    /**
     * 根据字段类型推导版本初始值
     */
    private Object resolveInitialVersion(Class<?> fieldType) {
        return switch (fieldType.getName()) {
            case "int", "java.lang.Integer" -> 0;
            case "long", "java.lang.Long" -> 0L;
            case "short", "java.lang.Short" -> (short) 0;
            default -> 0;
        };
    }
}
