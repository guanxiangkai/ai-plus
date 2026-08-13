package io.github.guanxiangkai.web.plus.core.enums;

import java.util.Map;

/**
 * 是/否 枚举
 * <p>
 * 使用 GraalVM JDK 25 特性：
 * - 静态缓存优化（O(1) 查找）
 * - Switch 表达式
 * - 不可变集合
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum YesNoEnum implements BaseEnum<Integer> {

    /**
     * 否
     */
    NO(0, "否"),

    /**
     * 是
     */
    YES(1, "是");

    private final Integer code;
    private final String description;

    /**
     * 静态缓存（GraalVM 编译时初始化）
     */
    private static final Map<Integer, YesNoEnum> CODE_MAP =
            BaseEnum.createCodeMap(YesNoEnum.class, YesNoEnum::getCode);

    YesNoEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举（O(1) 查找）
     */
    public static YesNoEnum fromCode(Integer code) {
        return CODE_MAP.getOrDefault(code, NO);
    }

    /**
     * 从布尔值转换
     */
    public static YesNoEnum fromBoolean(boolean value) {
        return value ? YES : NO;
    }

    /**
     * 使用 Switch 表达式判断
     */
    public static String getLabel(YesNoEnum value) {
        return switch (value) {
            case YES -> "是";
            case NO -> "否";
        };
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * 转换为布尔值
     */
    public boolean toBoolean() {
        return this == YES;
    }
}

