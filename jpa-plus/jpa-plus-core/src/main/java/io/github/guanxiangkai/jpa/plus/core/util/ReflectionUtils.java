package io.github.guanxiangkai.jpa.plus.core.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 反射工具类
 *
 * <p>集中管理所有反射操作，避免反射代码在各模块中重复。
 * 所有方法均为线程安全的静态方法。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class ReflectionUtils {

    /**
     * Per-class field lookup cache. ClassValue keeps entries tied to the declaring Class lifecycle,
     * avoiding global ClassLoader pinning while removing repeated hierarchy scans from hot paths.
     */
    private static final ClassValue<Map<String, Field>> FIELD_LOOKUP_CACHE = new ClassValue<>() {
        @Override
        protected Map<String, Field> computeValue(Class<?> type) {
            Map<String, Field> fields = new LinkedHashMap<>();
            Class<?> current = type;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    fields.putIfAbsent(field.getName(), field);
                }
                current = current.getSuperclass();
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
    };

    /**
     * Per-declaring-class field accessor cache. MethodHandle avoids repeated Field.get/set calls
     * while ClassValue keeps accessors tied to class unloading.
     */
    private static final ClassValue<Map<String, FieldAccessor>> FIELD_ACCESSOR_CACHE = new ClassValue<>() {
        @Override
        protected Map<String, FieldAccessor> computeValue(Class<?> type) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
                Map<String, FieldAccessor> accessors = new LinkedHashMap<>();
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    accessors.put(field.getName(), new FieldAccessor(
                            lookup.unreflectGetter(field),
                            Modifier.isFinal(field.getModifiers()) ? null : lookup.unreflectSetter(field)
                    ));
                }
                return Collections.unmodifiableMap(accessors);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot create field accessors for: " + type.getName(), e);
            }
        }
    };

    private ReflectionUtils() {
    }

    /**
     * 在类层次结构中查找指定名称的字段
     *
     * @param clazz 起始类
     * @param name  字段名
     * @return 找到的字段，未找到返回 {@code null}
     */
    public static Field findField(Class<?> clazz, String name) {
        if (clazz == null || name == null || name.isBlank()) return null;
        return FIELD_LOOKUP_CACHE.get(clazz).get(name);
    }

    /**
     * 获取类层次结构中的所有字段（包括父类），并设置可访问
     *
     * @param clazz 目标类
     * @return 所有字段的不可变列表
     */
    public static List<Field> getHierarchyFields(Class<?> clazz) {
        return List.copyOf(FIELD_LOOKUP_CACHE.get(clazz).values());
    }

    /**
     * 安全设置字段可访问并获取值
     *
     * @param entity 实体对象
     * @param field  字段
     * @return 字段值
     */
    public static Object getFieldValue(Object entity, Field field) {
        try {
            return accessorFor(field).getter().invoke(entity);
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot access field: " + field.getName(), e);
        }
    }

    /**
     * 安全设置字段值
     *
     * @param entity 实体对象
     * @param field  字段
     * @param value  要设置的值
     */
    public static void setFieldValue(Object entity, Field field, Object value) {
        try {
            FieldAccessor accessor = accessorFor(field);
            if (accessor.setter() == null) {
                throw new IllegalStateException("Field is final and cannot be set: " + field.getName());
            }
            accessor.setter().invoke(entity, value);
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot set field: " + field.getName(), e);
        }
    }

    private static FieldAccessor accessorFor(Field field) {
        FieldAccessor accessor = FIELD_ACCESSOR_CACHE.get(field.getDeclaringClass()).get(field.getName());
        if (accessor == null) {
            throw new IllegalStateException("No accessor cached for field: " + field);
        }
        return accessor;
    }

    /**
     * 通用反射实例化工具（枚举取第一个常量，普通类调无参构造）
     *
     * <p>消除各模块中重复的 {@code instantiate()} 私有方法。</p>
     *
     * @param clazz 目标类型
     * @param <T>   目标泛型
     * @return 实例化结果
     * @throws IllegalStateException 实例化失败时抛出
     */
    public static <T> T instantiate(Class<T> clazz) {
        try {
            if (clazz.isEnum()) {
                T[] constants = clazz.getEnumConstants();
                if (constants != null && constants.length > 0) {
                    return constants[0];
                }
                throw new IllegalStateException("Enum " + clazz.getName() + " has no constants");
            }
            return clazz.getDeclaredConstructor().newInstance();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot instantiate: " + clazz.getName(), e);
        }
    }

    private record FieldAccessor(MethodHandle getter, MethodHandle setter) {
    }
}
