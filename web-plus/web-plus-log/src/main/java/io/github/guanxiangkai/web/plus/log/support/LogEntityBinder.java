package io.github.guanxiangkai.web.plus.log.support;

import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

/**
 * 日志实体字段绑定工具
 *
 * <p>
 * 通过反射将 AOP 切面采集的公共字段和类型专属字段写入用户自定义的日志实体。
 * 约定：用户实体中与日志字段同名的字段将被自动填充；不存在的字段静默跳过。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public final class LogEntityBinder {

    private LogEntityBinder() {
    }

    /**
     * 填充 {@link BaseLog} 公共字段（通过 setter）
     *
     * @param status 日志状态字符串（典型值：{@code SUCCESS / FAIL / RUNNING / WARN / SKIP}）
     */
    public static void bindCommon(BaseLog entity,
                                  String traceId,
                                  String userId,
                                  String username,
                                  String tenantId,
                                  String clientIp,
                                  String status,
                                  String message) {
        entity.setTraceId(traceId);
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setTenantId(tenantId);
        entity.setClientIp(clientIp);
        entity.setStatus(status);
        entity.setMessage(message);
        entity.setLogTime(LocalDateTime.now());
    }

    /**
     * 通过反射向实体设置命名字段（字段不存在时静默跳过）
     *
     * @param entity    目标实体
     * @param fieldName 字段名（需与实体字段名完全匹配）
     * @param value     字段值
     */
    public static void set(Object entity, String fieldName, Object value) {
        if (entity == null || fieldName == null || value == null) return;
        Field field = findField(entity.getClass(), fieldName);
        if (field == null) return;
        try {
            field.setAccessible(true);
            field.set(entity, value);
        } catch (IllegalArgumentException e) {
            log.warn("[web-plus] LogEntityBinder: 字段类型不匹配 field='{}' on '{}': 期望 {} 实际 {}",
                    fieldName, entity.getClass().getSimpleName(),
                    field.getType().getSimpleName(), value.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn("[web-plus] LogEntityBinder: 设置字段 '{}' 失败: exception={}",
                    fieldName, e.getClass().getSimpleName());
        }
    }

    /**
     * 通过反射读取实体字段值（字段不存在时返回 {@code null}）
     *
     * @param entity    目标实体
     * @param fieldName 字段名
     * @return 字段值，字段不存在或访问失败时返回 {@code null}
     */
    public static Object get(Object entity, String fieldName) {
        if (entity == null || fieldName == null) return null;
        Field field = findField(entity.getClass(), fieldName);
        if (field == null) return null;
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (Exception ignored) {
            return null;
        }
    }

    // ── internal ────────────────────────────────────────────────────────────

    private static Field findField(Class<?> clazz, String name) {
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            try {
                return curr.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                curr = curr.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 通过注解中指定的 entity class 创建实例
     *
     * @param entityClass 实体类（必须有无参构造器）
     * @return 实体实例，失败时返回 null
     */
    public static BaseLog newInstance(Class<?> entityClass) {
        if (entityClass == null || entityClass == Void.class) return null;
        try {
            Object obj = entityClass.getDeclaredConstructor().newInstance();
            if (obj instanceof BaseLog base) return base;
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
