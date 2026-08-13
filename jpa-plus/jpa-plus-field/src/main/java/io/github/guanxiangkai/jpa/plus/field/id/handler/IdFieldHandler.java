package io.github.guanxiangkai.jpa.plus.field.id.handler;

import io.github.guanxiangkai.jpa.plus.core.field.FieldHandler;
import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.field.id.annotation.AutoId;
import io.github.guanxiangkai.jpa.plus.field.id.enums.IdType;
import io.github.guanxiangkai.jpa.plus.field.id.generator.SnowflakeIdGenerator;
import io.github.guanxiangkai.jpa.plus.field.id.spi.IdGenerator;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * ID 自动生成字段处理器
 *
 * <p>实现 {@link FieldHandler}，在保存前自动为 {@link AutoId @AutoId} 标注的字段生成唯一 ID。
 * 仅在字段值为 {@code null} 时触发，已有值的字段不会被覆盖。</p>
 *
 * <h3>执行顺序</h3>
 * <p>{@code order=10}，在所有其他字段处理器之前执行，确保 ID 在加密、审计等处理之前就绑定。</p>
 *
 * <h3>策略解析</h3>
 * <ol>
 *   <li>读取字段上 {@code @AutoId} 注解的 {@code value()} 值</li>
 *   <li>如果为 {@link IdType#AUTO}，使用全局配置的默认策略</li>
 *   <li>根据最终策略调用对应生成器：
 *       <ul>
 *         <li>{@link IdType#SNOWFLAKE} → {@link SnowflakeIdGenerator#nextId()}</li>
 *         <li>{@link IdType#UUID} → {@link UUID#randomUUID()}</li>
 *         <li>{@link IdType#CUSTOM} → 用户实现的 {@link IdGenerator#generate(Field)}</li>
 *       </ul>
 *   </li>
 *   <li>根据字段类型自动转换（如 Long 字段接收 Snowflake Long 值，String 字段接收字符串形式）</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Entity
 * public class Order {
 *     @Id
 *     @AutoId // 跟随全局配置
 *     private Long id;
 *
 *     @AutoId(IdType.UUID) // 显式指定 UUID
 *     private String traceId;
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @see AutoId
 * @see IdType
 * @see SnowflakeIdGenerator
 * @see IdGenerator
 * @since 1.0.0
 */
@Slf4j
public class IdFieldHandler implements FieldHandler {

    /**
     * 全局默认 ID 策略（从配置 {@code jpa-plus.id-generator.type} 读取）
     */
    private final IdType defaultType;

    /**
     * 雪花算法生成器（当策略为 SNOWFLAKE 时使用，可为 null）
     */
    private final SnowflakeIdGenerator snowflakeGenerator;

    /**
     * 自定义 ID 生成器（当策略为 CUSTOM 时使用，可为 null）
     */
    private final IdGenerator customGenerator;

    /**
     * @param defaultType        全局默认策略（AUTO 时内部回退为 SNOWFLAKE）
     * @param snowflakeGenerator 雪花算法生成器（可为 null，仅 SNOWFLAKE 策略需要）
     * @param customGenerator    自定义生成器（可为 null，仅 CUSTOM 策略需要）
     */
    public IdFieldHandler(IdType defaultType,
                          SnowflakeIdGenerator snowflakeGenerator,
                          IdGenerator customGenerator) {
        // AUTO 回退为 SNOWFLAKE
        this.defaultType = (defaultType == null || defaultType == IdType.AUTO) ? IdType.SNOWFLAKE : defaultType;
        this.snowflakeGenerator = snowflakeGenerator;
        this.customGenerator = customGenerator;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean supports(Field field) {
        return field.isAnnotationPresent(AutoId.class);
    }

    @Override
    public void beforeSave(Object entity, Field field) {
        try {
            Object currentValue = ReflectionUtils.getFieldValue(entity, field);
            if (currentValue != null) {
                return; // 已有值，不覆盖
            }

            IdType effectiveType = resolveType(field);
            Object generatedId = generate(effectiveType, field);
            Object convertedId = convertToFieldType(generatedId, field);

            ReflectionUtils.setFieldValue(entity, field, convertedId);

            if (log.isDebugEnabled()) {
                log.debug("@AutoId generated: {}.{} = {} (strategy={})",
                        entity.getClass().getSimpleName(), field.getName(), convertedId, effectiveType);
            }
        } catch (Exception e) {
            log.error("ID 生成失败: field={}", field.getName(), e);
            throw new IllegalStateException("Failed to generate ID for field: " + field.getName(), e);
        }
    }

    // ─── 内部方法 ───

    /**
     * 解析字段的有效 ID 策略（注解值 → 全局默认）
     */
    private IdType resolveType(Field field) {
        AutoId annotation = field.getAnnotation(AutoId.class);
        IdType annotationType = annotation.value();
        if (annotationType == IdType.AUTO) {
            return defaultType; // 回退到全局配置
        }
        return annotationType;
    }

    /**
     * 根据策略生成 ID
     */
    private Object generate(IdType type, Field field) {
        return switch (type) {
            case SNOWFLAKE -> {
                if (snowflakeGenerator == null) {
                    throw new IllegalStateException(
                            "SnowflakeIdGenerator is not configured. " +
                                    "Ensure jpa-plus.id-generator.snowflake.* properties are set.");
                }
                yield snowflakeGenerator.nextId();
            }
            case UUID -> UUID.randomUUID().toString().replace("-", "");
            case CUSTOM -> {
                if (customGenerator == null) {
                    throw new IllegalStateException(
                            "No IdGenerator bean found for CUSTOM strategy. " +
                                    "Please implement IdGenerator interface and register as a Spring Bean.");
                }
                Object id = customGenerator.generate(field);
                if (id == null) {
                    throw new IllegalStateException(
                            "IdGenerator.generate() returned null for field: " + field.getName());
                }
                yield id;
            }
            case AUTO -> throw new IllegalStateException("AUTO should have been resolved to a concrete type");
        };
    }

    /**
     * 将生成的 ID 转换为字段兼容的类型
     *
     * <p>例如：Snowflake 生成 Long，若字段为 String 类型，自动转为字符串。</p>
     */
    private Object convertToFieldType(Object id, Field field) {
        Class<?> fieldType = field.getType();

        // 类型已匹配
        if (fieldType.isInstance(id)) {
            return id;
        }

        // Long → String
        if (fieldType == String.class) {
            return String.valueOf(id);
        }

        // Long ↔ 数值类型互转
        if (id instanceof Number number) {
            if (fieldType == Long.class || fieldType == long.class) {
                return number.longValue();
            }
            if (fieldType == Integer.class || fieldType == int.class) {
                return number.intValue();
            }
        }

        // String → Long（UUID 字符串不适用，但 CUSTOM 策略可能返回数字字符串）
        if (id instanceof String str && (fieldType == Long.class || fieldType == long.class)) {
            return Long.parseLong(str);
        }

        return id;
    }
}

