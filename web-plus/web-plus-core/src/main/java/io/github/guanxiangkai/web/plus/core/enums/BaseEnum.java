package io.github.guanxiangkai.web.plus.core.enums;

import io.github.guanxiangkai.web.plus.core.model.OptionItem;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基础枚举接口
 * <p>
 * GraalVM JDK 25 + Spring Boot 4 特性：
 * 1. 使用 sealed 接口（JDK 17+）增强类型安全
 * 2. 提供高效的静态缓存查找方法
 * 3. 支持 GraalVM Native Image 编译
 * 4. 函数式 API 设计
 * </p>
 * <p>
 * 所有业务枚举应实现此接口，提供统一的编码和描述获取方式
 * </p>
 *
 * @param <T> 编码类型
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface BaseEnum<T> {

    /**
     * 根据编码查找枚举实例（遍历查找，O(n)）
     * <p>
     * 建议子类使用静态 Map 缓存实现 O(1) 查找
     * </p>
     *
     * @param code      编码值
     * @param enumClass 枚举类型
     * @param <T>       编码类型
     * @param <E>       枚举类型
     * @return 匹配的枚举实例，未找到时返回 null
     */
    static <T, E extends Enum<E> & BaseEnum<T>> E getByCode(T code, Class<E> enumClass) {
        if (code == null || enumClass == null) {
            return null;
        }

        return Arrays.stream(enumClass.getEnumConstants())
                .filter(e -> code.equals(e.getCode()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 创建枚举缓存 Map（O(1) 查找）
     * <p>
     * 推荐在枚举类中使用此方法创建静态缓存：
     * <pre>{@code
     * private static final Map<String, MyEnum> CODE_MAP =
     *     BaseEnum.createCodeMap(MyEnum.class, MyEnum::getCode);
     * }</pre>
     * </p>
     *
     * @param enumClass  枚举类型
     * @param codeGetter 编码获取函数
     * @param <T>        编码类型
     * @param <E>        枚举类型
     * @return 不可变的编码映射 Map
     */
    static <T, E extends Enum<E> & BaseEnum<T>> Map<T, E> createCodeMap(
            Class<E> enumClass,
            Function<E, T> codeGetter) {

        return Arrays.stream(enumClass.getEnumConstants())
                .collect(Collectors.toUnmodifiableMap(
                        codeGetter,
                        Function.identity()
                ));
    }

    /**
     * 判断编码是否有效
     *
     * @param code      编码值
     * @param enumClass 枚举类型
     * @param <T>       编码类型
     * @param <E>       枚举类型
     * @return true 表示编码有效
     */
    static <T, E extends Enum<E> & BaseEnum<T>> boolean isValidCode(T code, Class<E> enumClass) {
        return getByCode(code, enumClass) != null;
    }

    /**
     * 获取枚举编码
     *
     * @return 编码值
     */
    T getCode();

    /**
     * 获取枚举描述
     *
     * @return 描述信息
     */
    String getDescription();

    /**
     * 将枚举数组转换为下拉框选项列表
     * <p>
     * 使用示例：
     * <pre>
     * // 获取枚举下拉框选项
     * List&lt;OptionItem&gt; options = BaseEnum.toOptions(StatusEnum.values());
     *
     * // 在 Controller 中返回
     * &#64;GetMapping("/options")
     * public ApiResponse&lt;List&lt;OptionItem&gt;&gt; getOptions() {
     *     return ApiResponse.ok(BaseEnum.toOptions(StatusEnum.values()));
     * }
     * </pre>
     * </p>
     *
     * @param enums 枚举数组
     * @param <T>   编码类型
     * @param <E>   枚举类型
     * @return 选项列表，label 为描述，value 为编码
     */
    static <T, E extends Enum<E> & BaseEnum<T>> List<OptionItem> toOptions(E[] enums) {
        if (enums == null || enums.length == 0) {
            return List.of();
        }
        return Arrays.stream(enums)
                .map(e -> OptionItem.of(
                        e.getDescription(),
                        e.getCode() == null ? null : e.getCode().toString()
                ))
                .toList();
    }

    /**
     * 将枚举 Class 转换为下拉框选项列表
     * <p>
     * 使用示例：
     * <pre>
     * List&lt;OptionItem&gt; options = BaseEnum.toOptions(StatusEnum.class);
     * </pre>
     * </p>
     *
     * @param enumClass 枚举类型
     * @param <T>       编码类型
     * @param <E>       枚举类型
     * @return 选项列表
     */
    static <T, E extends Enum<E> & BaseEnum<T>> List<OptionItem> toOptions(Class<E> enumClass) {
        if (enumClass == null) {
            return List.of();
        }
        return toOptions(enumClass.getEnumConstants());
    }
}
