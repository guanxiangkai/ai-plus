package io.github.guanxiangkai.jpa.plus.query.plan;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
     * 按目标类隔离映射计划缓存，避免不同类加载器中同名类型共享计划。
     *
     * <p>每个目标类最多缓存 256 种有序列标签组合，防止动态投影持续占用内存。</p>
     */
    private static final ClassValue<Cache<List<String>, MappingPlan<?>>> CACHE = new ClassValue<>() {
        @Override
        protected Cache<List<String>, MappingPlan<?>> computeValue(Class<?> type) {
            return Caffeine.newBuilder().maximumSize(256).build();
        }
    };
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /**
     * 编译或复用指定目标类型与有序列标签的映射计划。
     *
     * @param targetType 具有可访问无参构造器的目标类型
     * @param columns 按结果集位置排列的列定义，优先使用别名匹配字段
     * @param <R> 结果类型
     * @return 不可变字段映射计划；容量淘汰后可重新编译
     * @throws JpaPlusException 无法访问构造器、字段或 setter 时抛出
     */
    public static <R> MappingPlan<R> compile(Class<R> targetType, List<SelectColumn> columns) {
        List<String> columnLabels = new ArrayList<>(columns.size());
        for (SelectColumn sc : columns) {
            columnLabels.add(sc.column().alias() != null ? sc.column().alias() : sc.column().columnName());
        }
        List<String> cacheKey = List.copyOf(columnLabels);
        MappingPlan<?> plan = CACHE.get(targetType).get(cacheKey, _ -> doCompile(targetType, cacheKey));
        return plan.castTo(targetType);
    }

    private static MappingPlan<?> doCompile(Class<?> targetType, List<String> columnLabels) {
        try {
            MethodHandles.Lookup targetLookup = MethodHandles.privateLookupIn(targetType, LOOKUP);
            MethodHandle constructorHandle = targetLookup.findConstructor(targetType, MethodType.methodType(void.class))
                    .asType(MethodType.methodType(Object.class));
            List<FieldMapping> mappings = new ArrayList<>();

            for (int i = 0; i < columnLabels.size(); i++) {
                String fieldName = columnLabels.get(i);

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
