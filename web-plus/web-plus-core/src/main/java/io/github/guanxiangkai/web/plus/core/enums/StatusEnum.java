package io.github.guanxiangkai.web.plus.core.enums;

import io.github.guanxiangkai.web.plus.core.model.OptionItem;

import java.util.List;
import java.util.Map;

/**
 * 状态枚举示例
 * <p>
 * 演示如何使用 BaseEnum 的 toOptions 方法生成下拉框选项
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum StatusEnum implements BaseEnum<Integer> {

    /**
     * 禁用
     */
    DISABLED(0, "禁用"),

    /**
     * 启用
     */
    ENABLED(1, "启用");

    /**
     * 静态缓存（GraalVM 编译时初始化）
     */
    private static final Map<Integer, StatusEnum> CODE_MAP =
            BaseEnum.createCodeMap(StatusEnum.class, StatusEnum::getCode);
    private final Integer code;
    private final String description;

    StatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举（O(1) 查找）
     */
    public static StatusEnum fromCode(Integer code) {
        return CODE_MAP.getOrDefault(code, DISABLED);
    }

    /**
     * 获取下拉框选项列表
     * <p>
     * 使用示例：
     * <pre>
     * // 在 Controller 中
     * &#64;GetMapping("/status/options")
     * public ApiResponse&lt;List&lt;OptionItem&gt;&gt; getStatusOptions() {
     *     return ApiResponse.ok(StatusEnum.getOptions());
     * }
     * </pre>
     * </p>
     */
    public static List<OptionItem> getOptions() {
        return BaseEnum.toOptions(StatusEnum.values());
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
