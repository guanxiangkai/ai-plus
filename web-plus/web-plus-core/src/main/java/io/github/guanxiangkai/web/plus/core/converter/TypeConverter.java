package io.github.guanxiangkai.web.plus.core.converter;

/**
 * 类型转换器接口
 * <p>
 * 注册具体的转换实现（如 MapStruct 生成）可由 {@link EntityConverter} 自动发现并优先使用。
 * </p>
 *
 * @param <S> 源类型
 * @param <T> 目标类型
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface TypeConverter<S, T> {

    /**
     * 正向转换：S -&gt; T
     */
    T convert(S source);

    /**
     * 反向转换：T -&gt; S（可选实现，默认抛出 UnsupportedOperationException）
     */
    default S convertBack(T target) {
        throw new UnsupportedOperationException("该转换器未实现反向转换");
    }
}
