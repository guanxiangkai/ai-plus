package io.github.guanxiangkai.web.plus.excel.enums;

import io.github.guanxiangkai.web.plus.core.enums.BaseEnum;

import java.util.Map;

/**
 * Excel 样式策略枚举
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum ExcelStyleType implements BaseEnum<String> {

    DEFAULT("DEFAULT", "默认样式"),
    PROFESSIONAL("PROFESSIONAL", "专业样式"),
    MINIMAL("MINIMAL", "简约样式"),
    COLORFUL("COLORFUL", "彩色样式"),
    PRINT("PRINT", "打印样式");

    private static final Map<String, ExcelStyleType> CODE_MAP =
            BaseEnum.createCodeMap(ExcelStyleType.class, ExcelStyleType::getCode);
    private final String code;
    private final String description;

    ExcelStyleType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ExcelStyleType fromCode(String code) {
        return CODE_MAP.getOrDefault(code, DEFAULT);
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public boolean shouldFreezeHeader() {
        return switch (this) {
            case PROFESSIONAL, PRINT -> true;
            case DEFAULT, MINIMAL, COLORFUL -> false;
        };
    }

    public boolean useAlternateRowColor() {
        return this == COLORFUL;
    }
}

