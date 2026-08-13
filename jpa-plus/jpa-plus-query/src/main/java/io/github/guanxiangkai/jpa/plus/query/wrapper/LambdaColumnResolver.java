package io.github.guanxiangkai.jpa.plus.query.wrapper;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;
import io.github.guanxiangkai.jpa.plus.core.util.NamingUtils;
import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;
import io.github.guanxiangkai.jpa.plus.query.metadata.TableMeta;
import jakarta.persistence.Column;

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lambda 列名解析器
 *
 * <p>从 {@link TypedGetter} Lambda 方法引用中提取方法名，推导列名。
 * 支持 {@code @Column} 注解覆盖列名。</p>
 *
 * <p>使用 {@link MethodHandles#privateLookupIn(Class, MethodHandles.Lookup)} 访问 Lambda 元数据，
 * 在 Java 17+ 模块系统下零非法反射警告。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class LambdaColumnResolver {

    private static final ClassValue<Map<String, ResolvedColumn>> COLUMN_CACHE = new ClassValue<>() {
        @Override
        protected Map<String, ResolvedColumn> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    /**
     * Lambda writeReplace MethodHandle 缓存，随 Lambda class 生命周期自动释放。
     */
    private static final ClassValue<MethodHandle> WRITE_REPLACE_CACHE = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
                return lookup.unreflect(type.getDeclaredMethod("writeReplace"));
            } catch (ReflectiveOperationException e) {
                throw new JpaPlusException(
                        "Cannot access writeReplace MethodHandle on lambda class: " + type.getName(), e);
            }
        }
    };

    private LambdaColumnResolver() {
    }

    /**
     * 从 Lambda 中解析列名
     */
    public static <T> String resolve(TypedGetter<T, ?> func) {
        return resolveMetadata(func).columnName();
    }

    /**
     * 从 Lambda 中解析 ColumnMeta
     */
    public static <T> ColumnMeta resolveColumn(TypedGetter<T, ?> func, TableMeta table) {
        ResolvedColumn resolved = resolveMetadata(func);
        return ColumnMeta.of(table, resolved.columnName(), resolved.fieldType());
    }

    /**
     * 从 Lambda 中解析字段类型
     */
    public static <T> Class<?> resolveFieldType(TypedGetter<T, ?> func) {
        return resolveMetadata(func).fieldType();
    }

    private static ResolvedColumn resolveMetadata(TypedGetter<?, ?> func) {
        SerializedLambda lambda = getSerializedLambda(func);
        String methodName = lambda.getImplMethodName();
        Class<?> implClass = resolveImplClass(lambda, func.getClass());

        return COLUMN_CACHE.get(implClass)
                .computeIfAbsent(methodName, _ -> doResolve(implClass, methodName));
    }

    private static ResolvedColumn doResolve(Class<?> clazz, String methodName) {
        String fieldName = methodToFieldName(methodName);
        Field field = ReflectionUtils.findField(clazz, fieldName);
        Class<?> fieldType = field != null ? field.getType() : Object.class;

        if (field != null) {
            Column column = field.getAnnotation(Column.class);
            if (column != null && !column.name().isEmpty()) {
                return new ResolvedColumn(column.name(), fieldType);
            }
        }

        return new ResolvedColumn(NamingUtils.camelToSnake(fieldName), fieldType);
    }

    private static Class<?> resolveImplClass(SerializedLambda lambda, Class<?> funcClass) {
        String className = lambda.getImplClass().replace('/', '.');
        ClassLoader loader = funcClass.getClassLoader();
        try {
            return loader != null
                    ? Class.forName(className, false, loader)
                    : Class.forName(className);
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ex) {
                throw new JpaPlusException("Cannot resolve class: " + className, ex);
            }
        }
    }

    private static String methodToFieldName(String methodName) {
        String fieldName;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            fieldName = methodName.substring(3);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            fieldName = methodName.substring(2);
        } else {
            fieldName = methodName;
        }
        return fieldName.substring(0, 1).toLowerCase(Locale.ROOT) + fieldName.substring(1);
    }

    /**
     * 获取 Lambda 的 SerializedLambda。
     *
     * <p>2.0 起只通过 {@link MethodHandles#privateLookupIn} 调用，避免 JVM 强封装警告。
     * 若模块未开放访问，直接抛错并要求调用方调整模块声明。</p>
     */
    private static SerializedLambda getSerializedLambda(Serializable func) {
        try {
            return (SerializedLambda) WRITE_REPLACE_CACHE.get(func.getClass()).bindTo(func).invokeWithArguments();
        } catch (Throwable t) {
            throw new JpaPlusException(
                    "Failed to extract SerializedLambda from function reference. " +
                            "Ensure the entity module opens the lambda package to jpa-plus-query.", t);
        }
    }

    private record ResolvedColumn(String columnName, Class<?> fieldType) {
    }
}
