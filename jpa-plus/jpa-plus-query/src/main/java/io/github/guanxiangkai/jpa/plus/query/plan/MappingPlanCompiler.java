package io.github.guanxiangkai.jpa.plus.query.plan;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;
import io.github.guanxiangkai.jpa.plus.core.util.NamingUtils;
import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.query.wrapper.SelectColumn;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 映射计划编译器
 *
 * <p>根据目标类型和 SELECT 列预计算映射方案，
 * 使用 {@link MethodHandle} 替代反射提高性能。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class MappingPlanCompiler {

    /**
     * P1-8 fix: Replaced {@code Collections.synchronizedMap(LinkedHashMap)} with
     * {@code ConcurrentHashMap}. The previous implementation held a single global mutex for the
     * entire duration of every {@code computeIfAbsent} call, including the expensive
     * {@code doCompile()} work (reflection, MethodHandle lookups), serializing ALL callers.
     * {@code ConcurrentHashMap.computeIfAbsent} uses per-bin locking, so different cache keys
     * are computed in parallel with no global contention.
     *
     * <p>LRU eviction is intentionally omitted: the key space is bounded by the number of distinct
     * (entity-type, column-list) permutations used at runtime, which is small and stable.
     * If the application dynamically generates an unbounded number of column lists, re-introduce
     * eviction via {@link java.util.concurrent.ConcurrentLinkedHashMap} or Caffeine.</p>
     */
    private static final ConcurrentHashMap<String, MappingPlan<?>> CACHE = new ConcurrentHashMap<>(256);
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /**
     * 编译映射计划
     */
    public static <R> MappingPlan<R> compile(Class<R> targetType, List<SelectColumn> columns) {
        // 缓存键使用有序列名连接，避免 hashCode() 碰撞导致错误缓存命中
        StringJoiner sj = new StringJoiner(",", targetType.getName() + "#", "");
        for (SelectColumn sc : columns) {
            sj.add(sc.column().alias() != null ? sc.column().alias() : sc.column().columnName());
        }
        String cacheKey = sj.toString();
        return CACHE.computeIfAbsent(cacheKey, _ -> doCompile(targetType, columns)).castTo(targetType);
    }

    private static <R> MappingPlan<R> doCompile(Class<R> targetType, List<SelectColumn> columns) {
        try {
            MethodHandles.Lookup targetLookup = MethodHandles.privateLookupIn(targetType, LOOKUP);
            MethodHandle constructorHandle = targetLookup.findConstructor(targetType, MethodType.methodType(void.class))
                    .asType(MethodType.methodType(Object.class));
            List<FieldMapping> mappings = new ArrayList<>();

            for (int i = 0; i < columns.size(); i++) {
                SelectColumn sc = columns.get(i);
                String fieldName = sc.column().alias() != null ? sc.column().alias() : sc.column().columnName();

                // 尝试查找同名字段
                Field field = ReflectionUtils.findField(targetType, fieldName);
                if (field == null) {
                    // 尝试蛇形转驼峰
                    field = ReflectionUtils.findField(targetType, NamingUtils.snakeToCamel(fieldName));
                }

                if (field != null) {
                    MethodHandle setter = findSetter(targetType, targetLookup, field);
                    mappings.add(new FieldMapping(fieldName, i + 1, setter, field.getType()));
                }
            }

            return new MappingPlan<>(targetType, constructorHandle, mappings);
        } catch (NoSuchMethodException e) {
            throw new JpaPlusException("Target type must have a no-arg constructor: " + targetType.getName(), e);
        } catch (IllegalAccessException e) {
            throw new JpaPlusException("Cannot access no-arg constructor: " + targetType.getName(), e);
        }
    }

    private static MethodHandle findSetter(Class<?> type, MethodHandles.Lookup targetLookup, Field field) {
        String setterName = "set" + field.getName().substring(0, 1).toUpperCase(Locale.ROOT) + field.getName().substring(1);
        try {
            return targetLookup.findVirtual(type, setterName, MethodType.methodType(void.class, field.getType()));
        } catch (Exception e) {
            try {
                return targetLookup.unreflectSetter(field);
            } catch (IllegalAccessException ex) {
                throw new JpaPlusException(
                        "Cannot access setter or field for mapping: " + type.getName() + "." + field.getName() +
                                ". Provide an accessible setter or open the target package to jpa-plus-query.", ex);
            }
        }
    }
}
