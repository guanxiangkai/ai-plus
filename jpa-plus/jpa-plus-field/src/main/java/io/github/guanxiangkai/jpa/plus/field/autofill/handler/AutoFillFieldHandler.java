package io.github.guanxiangkai.jpa.plus.field.autofill.handler;

import io.github.guanxiangkai.jpa.plus.core.exception.FieldProcessingException;
import io.github.guanxiangkai.jpa.plus.core.field.FieldHandler;
import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.CreateBy;
import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.CreateTime;
import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.UpdateBy;
import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.UpdateTime;
import io.github.guanxiangkai.jpa.plus.field.autofill.spi.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.*;
import java.util.Date;

/**
 * 自动填充字段处理器
 *
 * <p>实现 {@link FieldHandler}，在保存前自动填充时间戳和操作人字段。
 * 支持以下注解：</p>
 *
 * <table>
 *   <tr><th>注解</th><th>填充时机</th><th>填充值</th></tr>
 *   <tr><td>{@link CreateTime}</td><td>创建时（字段为 null）</td><td>当前时间</td></tr>
 *   <tr><td>{@link UpdateTime}</td><td>每次保存</td><td>当前时间</td></tr>
 *   <tr><td>{@link CreateBy}</td><td>创建时（字段为 null）</td><td>当前操作人</td></tr>
 *   <tr><td>{@link UpdateBy}</td><td>每次保存</td><td>当前操作人</td></tr>
 * </table>
 *
 * <h3>创建 vs 更新的判定</h3>
 * <ul>
 *   <li>{@code @CreateTime} / {@code @CreateBy} —— 仅当字段值为 {@code null} 时填充
 *       （首次保存字段为 null → 填充；后续更新字段已有值 → 跳过）</li>
 *   <li>{@code @UpdateTime} / {@code @UpdateBy} —— 每次保存都覆盖</li>
 * </ul>
 *
 * <h3>执行顺序</h3>
 * <p>{@code order=20}，在 ID 生成（10）之后、加密（100）等处理之前执行。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Entity
 * public class User {
 *
 *     @Id
 *     @AutoId
 *     private Long id;
 *
 *     @CreateTime
 *     private LocalDateTime createTime;
 *
 *     @UpdateTime
 *     private LocalDateTime updateTime;
 *
 *     @CreateBy
 *     private String createBy;
 *
 *     @UpdateBy
 *     private String updateBy;
 *
 *     @Version
 *     private Integer version;
 *
 *     @LogicDelete
 *     private Integer deleted;
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @see CreateTime
 * @see UpdateTime
 * @see CreateBy
 * @see UpdateBy
 * @see CurrentUserProvider
 * @since 1.0.0
 */
@Slf4j
public class AutoFillFieldHandler implements FieldHandler {

    /**
     * 当前操作人提供者（可为 null，未配置时 @CreateBy/@UpdateBy 不生效）
     */
    private final CurrentUserProvider userProvider;

    /**
     * @param userProvider 当前操作人提供者（可为 null）
     */
    public AutoFillFieldHandler(CurrentUserProvider userProvider) {
        this.userProvider = userProvider;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(Field field) {
        return field.isAnnotationPresent(CreateTime.class)
                || field.isAnnotationPresent(UpdateTime.class)
                || field.isAnnotationPresent(CreateBy.class)
                || field.isAnnotationPresent(UpdateBy.class);
    }

    @Override
    public void beforeSave(Object entity, Field field) {
        try {
            // ── 时间填充 ──
            if (field.isAnnotationPresent(CreateTime.class)) {
                fillTimeIfNull(entity, field);
            }
            if (field.isAnnotationPresent(UpdateTime.class)) {
                fillTimeAlways(entity, field);
            }

            // ── 操作人填充 ──
            if (field.isAnnotationPresent(CreateBy.class)) {
                fillUserIfNull(entity, field);
            }
            if (field.isAnnotationPresent(UpdateBy.class)) {
                fillUserAlways(entity, field);
            }
        } catch (Exception e) {
            throw new FieldProcessingException(
                    "AutoFill failed for field '" + field.getName() + "' on " +
                            entity.getClass().getSimpleName(), e);
        }
    }

    // ─── 时间填充 ───

    /**
     * 创建时间：仅 null 时填充
     */
    private void fillTimeIfNull(Object entity, Field field) {
        Object current = ReflectionUtils.getFieldValue(entity, field);
        if (current == null) {
            Object time = resolveCurrentTime(field.getType());
            ReflectionUtils.setFieldValue(entity, field, time);
            logFill(entity, field, time);
        }
    }

    /**
     * 更新时间：每次覆盖
     */
    private void fillTimeAlways(Object entity, Field field) {
        Object time = resolveCurrentTime(field.getType());
        ReflectionUtils.setFieldValue(entity, field, time);
    }

    /**
     * 根据字段类型生成当前时间值
     *
     * <p>支持 JPA / Hibernate 常用的所有时间类型：</p>
     * <ul>
     *   <li>{@link LocalDateTime} —— 无时区日期时间（最常用）</li>
     *   <li>{@link LocalDate} —— 无时区日期</li>
     *   <li>{@link LocalTime} —— 无时区时间</li>
     *   <li>{@link OffsetDateTime} —— 带偏移量的日期时间</li>
     *   <li>{@link ZonedDateTime} —— 带时区的日期时间</li>
     *   <li>{@link Instant} —— UTC 时间戳</li>
     *   <li>{@link java.util.Date} —— JDK Date</li>
     *   <li>{@link java.sql.Timestamp} —— JDBC Timestamp</li>
     *   <li>{@link java.sql.Date} —— JDBC Date</li>
     *   <li>{@link Long} / {@code long} —— 毫秒时间戳</li>
     * </ul>
     */
    private Object resolveCurrentTime(Class<?> fieldType) {
        return switch (fieldType) {
            case Class<?> t when t == LocalDateTime.class -> LocalDateTime.now();
            case Class<?> t when t == LocalDate.class -> LocalDate.now();
            case Class<?> t when t == LocalTime.class -> LocalTime.now();
            case Class<?> t when t == OffsetDateTime.class -> OffsetDateTime.now();
            case Class<?> t when t == ZonedDateTime.class -> ZonedDateTime.now();
            case Class<?> t when t == Instant.class -> Instant.now();
            case Class<?> t when t == Date.class -> new Date();
            case Class<?> t when t == Timestamp.class -> new Timestamp(System.currentTimeMillis());
            case Class<?> t when t == java.sql.Date.class -> new java.sql.Date(System.currentTimeMillis());
            case Class<?> t when t == Long.class || t == long.class -> System.currentTimeMillis();
            default -> throw new IllegalStateException(
                    "Unsupported time field type: " + fieldType.getName() +
                            ". Supported: LocalDateTime, LocalDate, LocalTime, OffsetDateTime, " +
                            "ZonedDateTime, Instant, Date, Timestamp, Long");
        };
    }

    // ─── 操作人填充 ───

    /**
     * 创建人：仅 null 时填充
     */
    private void fillUserIfNull(Object entity, Field field) {
        if (userProvider == null) {
            log.debug("CurrentUserProvider not configured, skip @CreateBy for field: {}", field.getName());
            return;
        }
        Object current = ReflectionUtils.getFieldValue(entity, field);
        if (current == null) {
            Object user = userProvider.getCurrentUser();
            if (user != null) {
                Object converted = convertToFieldType(user, field);
                ReflectionUtils.setFieldValue(entity, field, converted);
                logFill(entity, field, converted);
            }
        }
    }

    /**
     * 更新人：每次覆盖
     */
    private void fillUserAlways(Object entity, Field field) {
        if (userProvider == null) {
            log.debug("CurrentUserProvider not configured, skip @UpdateBy for field: {}", field.getName());
            return;
        }
        Object user = userProvider.getCurrentUser();
        if (user != null) {
            Object converted = convertToFieldType(user, field);
            ReflectionUtils.setFieldValue(entity, field, converted);
        }
    }

    // ─── 类型转换 ───

    /**
     * 将操作人标识转换为字段兼容的类型
     *
     * <p>例如：CurrentUserProvider 返回 Long 用户 ID，字段为 String → 自动转字符串。</p>
     */
    private Object convertToFieldType(Object value, Field field) {
        Class<?> fieldType = field.getType();

        // 类型已匹配
        if (fieldType.isInstance(value)) {
            return value;
        }

        // → String
        if (fieldType == String.class) {
            return String.valueOf(value);
        }

        // Number → Long / Integer
        if (value instanceof Number number) {
            if (fieldType == Long.class || fieldType == long.class) {
                return number.longValue();
            }
            if (fieldType == Integer.class || fieldType == int.class) {
                return number.intValue();
            }
        }

        // String → Long / Integer
        if (value instanceof String str) {
            try {
                if (fieldType == Long.class || fieldType == long.class) {
                    return Long.parseLong(str);
                }
                if (fieldType == Integer.class || fieldType == int.class) {
                    return Integer.parseInt(str);
                }
            } catch (NumberFormatException e) {
                throw new FieldProcessingException(
                        "无法将 CurrentUser 值 '" + str + "' 转换为字段类型 " + fieldType.getSimpleName(), e);
            }
        }

        return value;
    }

    private void logFill(Object entity, Field field, Object value) {
        if (log.isDebugEnabled()) {
            log.debug("AutoFill: {}.{} = {}", entity.getClass().getSimpleName(), field.getName(), value);
        }
    }
}
