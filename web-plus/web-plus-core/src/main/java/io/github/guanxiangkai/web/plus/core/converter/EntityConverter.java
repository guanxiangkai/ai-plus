package io.github.guanxiangkai.web.plus.core.converter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体转换工具类
 * <p>
 * 转换策略（按优先级）：
 * <ol>
 *   <li>优先使用已注册的 {@link TypeConverter}（如 MapStruct 生成，类型安全、性能最优）</li>
 *   <li>回退到 Spring {@code BeanUtils.copyProperties}（基于属性名匹配）</li>
 * </ol>
 * </p>
 *
 * <h3>注册自定义转换器</h3>
 * <pre>{@code
 * // 将 Spring Bean 注入 EntityConverter
 * EntityConverter.register(new PostConverter());
 * // 或在 Spring Boot 中通过 @Bean 自动注册（需配合 EntityConverterRegistrar）
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public final class EntityConverter {

    private static final Map<String, TypeConverter<?, ?>> CONVERTER_MAP = new ConcurrentHashMap<>();

    private EntityConverter() {
    }

    // ── 转换器注册 ────────────────────────────────────────────────

    /**
     * 注册转换器（双向注册：S->T 和 T->S）
     */
    public static void register(TypeConverter<?, ?> converter) {
        Type[] interfaces = converter.getClass().getGenericInterfaces();
        for (Type type : interfaces) {
            if (type instanceof ParameterizedType pt
                    && pt.getRawType() instanceof Class<?> raw
                    && TypeConverter.class.isAssignableFrom(raw)) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 2) {
                    String fwd = args[0].getTypeName() + "->" + args[1].getTypeName();
                    String rev = args[1].getTypeName() + "->" + args[0].getTypeName();
                    CONVERTER_MAP.put(fwd, converter);
                    CONVERTER_MAP.put(rev, converter);
                    log.debug("已注册转换器: {} <-> {}", args[0], args[1]);
                }
            }
        }
    }

    // ── 核心转换方法 ──────────────────────────────────────────────

    /**
     * 单对象转换
     */
    @SuppressWarnings("unchecked")
    public static <S, T> T convert(S source, Class<T> targetClass) {
        if (source == null) return null;
        String key = source.getClass().getName() + "->" + targetClass.getName();
        TypeConverter<S, T> specific = (TypeConverter<S, T>) CONVERTER_MAP.get(key);
        if (specific != null) {
            try {
                return specific.convert(source);
            } catch (Exception e) {
                log.warn("TypeConverter 转换失败，回退到 BeanUtils: {}", key, e);
            }
        }
        return convertWithBeanUtils(source, targetClass);
    }

    /**
     * 列表转换
     */
    public static <S, T> List<T> convertList(List<S> sources, Class<T> targetClass) {
        if (sources == null || sources.isEmpty()) return Collections.emptyList();
        List<T> result = new ArrayList<>(sources.size());
        for (S s : sources) result.add(convert(s, targetClass));
        return Collections.unmodifiableList(result);
    }

    /**
     * 属性复制（更新已有对象，跳过 null 值）
     */
    public static <S, T> void copyProperties(S source, T target) {
        if (source == null || target == null) return;
        if (source.getClass().isRecord()) {
            copyRecordProperties(source, target);
            return;
        }
        BeanUtils.copyProperties(source, target, getNullPropertyNames(source));
    }

    /**
     * DTO -> Entity
     */
    public static <D, E> E toEntity(D dto, Class<E> entityClass) {
        return convert(dto, entityClass);
    }

    /**
     * Entity -> VO
     */
    public static <E, V> V toVo(E entity, Class<V> voClass) {
        return convert(entity, voClass);
    }

    /**
     * Entity list -> VO list
     */
    public static <E, V> List<V> toVoList(List<E> entities, Class<V> voClass) {
        return convertList(entities, voClass);
    }

    /**
     * DTO -> Entity（属性覆盖，用于更新操作）
     */
    public static <D, E> void updateEntity(D dto, E entity) {
        copyProperties(dto, entity);
    }

    // ── 内部方法 ──────────────────────────────────────────────────

    private static <S, T> T convertWithBeanUtils(S source, Class<T> targetClass) {
        try {
            Constructor<T> ctor = targetClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            T target = ctor.newInstance();
            if (source.getClass().isRecord()) {
                copyRecordProperties(source, target);
            } else {
                BeanUtils.copyProperties(source, target);
            }
            return target;
        } catch (Exception e) {
            log.error("BeanUtils 转换失败: {} -> {}",
                    source.getClass().getSimpleName(), targetClass.getSimpleName(), e);
            throw new RuntimeException("对象转换失败: " + e.getMessage(), e);
        }
    }

    private static String[] getNullPropertyNames(Object source) {
        BeanWrapper src = new BeanWrapperImpl(source);
        return Arrays.stream(src.getPropertyDescriptors())
                .map(pd -> pd.getName())
                .filter(name -> src.getPropertyValue(name) == null)
                .toArray(String[]::new);
    }

    private static <S, T> void copyRecordProperties(S source, T target) {
        BeanWrapper targetWrapper = new BeanWrapperImpl(target);
        for (RecordComponent component : source.getClass().getRecordComponents()) {
            String name = component.getName();
            if (!targetWrapper.isWritableProperty(name)) {
                continue;
            }
            Object value = readRecordComponent(source, component);
            if (value != null) {
                targetWrapper.setPropertyValue(name, value);
            }
        }
    }

    private static Object readRecordComponent(Object source, RecordComponent component) {
        try {
            return component.getAccessor().invoke(source);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("读取 record 属性失败: " + component.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("读取 record 属性失败: " + component.getName(), cause);
        }
    }
}
