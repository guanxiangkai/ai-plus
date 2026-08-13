package io.github.guanxiangkai.web.plus.excel.enums;

import io.github.guanxiangkai.web.plus.core.enums.BaseEnum;

import java.util.Map;

/**
 * Excel 文件类型枚举
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum ExcelFileType implements BaseEnum<String> {

    XLSX("XLSX", "Excel 2007+", ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    XLS("XLS", "Excel 97-2003", ".xls", "application/vnd.ms-excel"),
    CSV("CSV", "CSV 文件", ".csv", "text/csv");

    private static final Map<String, ExcelFileType> CODE_MAP =
            BaseEnum.createCodeMap(ExcelFileType.class, ExcelFileType::getCode);

    private final String code;
    private final String description;
    private final String extension;
    private final String contentType;

    ExcelFileType(String code, String description, String extension, String contentType) {
        this.code = code;
        this.description = description;
        this.extension = extension;
        this.contentType = contentType;
    }

    public static ExcelFileType fromCode(String code) {
        return CODE_MAP.getOrDefault(code, XLSX);
    }

    public static ExcelFileType fromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return XLSX;
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".xls") && !lower.endsWith(".xlsx")) return XLS;
        if (lower.endsWith(".csv")) return CSV;
        return XLSX;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public String getExtension() {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }
}
