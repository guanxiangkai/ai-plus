package io.github.guanxiangkai.web.plus.excel.enums;

import io.github.guanxiangkai.web.plus.core.enums.BaseEnum;

import java.util.Map;

/**
 * Excel 操作类型枚举
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum ExcelOperationType implements BaseEnum<String> {

    IMPORT("IMPORT", "导入"),
    EXPORT("EXPORT", "导出"),
    TEMPLATE("TEMPLATE", "模板下载"),
    VALIDATE("VALIDATE", "验证");

    private static final Map<String, ExcelOperationType> CODE_MAP =
            BaseEnum.createCodeMap(ExcelOperationType.class, ExcelOperationType::getCode);
    private final String code;
    private final String description;

    ExcelOperationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ExcelOperationType fromCode(String code) {
        return CODE_MAP.getOrDefault(code, EXPORT);
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDescription() {
        return description;
    }
}

