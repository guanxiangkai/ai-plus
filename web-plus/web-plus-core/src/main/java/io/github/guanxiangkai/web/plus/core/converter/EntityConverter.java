package io.github.guanxiangkai.web.plus.core.converter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体转换工具类
 * <p>
 * 转换策略（按优先级）：
 * <ol>
 *   <li>优先使用已注册的 {@link TypeConverter}（如 MapStruct 生成，类型安全、性能最优）</li>
 *   <li>未注册转换器时，使用 Spring {@code BeanUtils.copyProperties}（基于属性名匹配）</li>
 * </ol>
 * 已注册转换器的正向和反向转换分别调用 {@link TypeConverter#convert(Object)} 与
 * {@link TypeConverter#convertBack(Object)}；转换器执行失败会显式抛出异常。
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

    private static final Map<ConversionKey, ConverterBinding> CONVERTER_MAP = new ConcurrentHashMap<>();

    private EntityConverter() {
    }

    // ── 转换器注册 ────────────────────────────────────────────────

    /**
     * 注册转换器。不同源、目标类型会注册 S-&gt;T 和 T-&gt;S；同类型仅注册正向转换。
     *
     * @param converter 泛型参数必须能在运行时解析为具体类型的转换器
     * @throws IllegalArgumentException 泛型参数未绑定、使用 {@code Object} 或参数化端点时抛出
     */
    public static void register(TypeConverter<?, ?> converter) {
        Objects.requireNonNull(converter, "converter 不能为 null");
        ResolvableType converterType = ResolvableType.forClass(converter.getClass()).as(TypeConverter.class);
        if (converterType == ResolvableType.NONE || converterType.hasUnresolvableGenerics()) {
            throw new IllegalArgumentException("TypeConverter 类型参数必须解析为具体类型，不能包含未绑定泛型: "
                    + converterType);
        }
        Class<?> sourceType = resolveConverterType(converterType, 0, "源");
        Class<?> targetType = resolveConverterType(converterType, 1, "目标");

        CONVERTER_MAP.put(new ConversionKey(sourceType, targetType), ConverterBinding.forward(converter));
        if (sourceType != targetType) {
            CONVERTER_MAP.put(new ConversionKey(targetType, sourceType), ConverterBinding.reverse(converter));
            log.debug("已注册双向转换器: {} <-> {}", sourceType.getName(), targetType.getName());
            return;
        }
        log.debug("已注册正向转换器: {} -> {}", sourceType.getName(), targetType.getName());
    }

    // ── 核心转换方法 ──────────────────────────────────────────────

    /**
     * 单对象转换
     */
    public static <S, T> T convert(S source, Class<T> targetClass) {
        if (source == null) return null;
        ConversionKey key = new ConversionKey(source.getClass(), targetClass);
        ConverterBinding binding = CONVERTER_MAP.get(key);
        if (binding != null) {
            try {
                return targetClass.cast(binding.convert(source));
            } catch (Exception e) {
                throw new IllegalStateException("已注册 TypeConverter 转换失败: " + key, e);
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

    private static Class<?> resolveConverterType(ResolvableType converterType, int genericIndex, String role) {
        ResolvableType genericType = converterType.getGeneric(genericIndex);
        Class<?> resolvedType = genericType.resolve();
        if (resolvedType == null || resolvedType == Object.class || genericType.hasGenerics()
                || genericType.hasUnresolvableGenerics()) {
            throw new IllegalArgumentException("TypeConverter " + role + "类型参数必须是非参数化的具体类型，不能为未绑定泛型或 Object: "
                    + converterType);
        }
        return resolvedType;
    }

    private record ConversionKey(Class<?> sourceType, Class<?> targetType) {
    }

    private record ConverterBinding(TypeConverter<?, ?> converter, boolean reverse) {

        private static ConverterBinding forward(TypeConverter<?, ?> converter) {
            return new ConverterBinding(converter, false);
        }

        private static ConverterBinding reverse(TypeConverter<?, ?> converter) {
            return new ConverterBinding(converter, true);
        }

        /**
         * 注册时已解析并以精确的源、目标类型建键，因此此处可集中完成泛型擦除后的调用。
         */
        @SuppressWarnings("unchecked")
        private Object convert(Object source) {
            TypeConverter<Object, Object> typedConverter = (TypeConverter<Object, Object>) converter;
            return reverse ? typedConverter.convertBack(source) : typedConverter.convert(source);
        }
    }

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
