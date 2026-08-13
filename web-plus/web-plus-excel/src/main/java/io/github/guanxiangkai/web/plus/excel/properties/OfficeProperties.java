package io.github.guanxiangkai.web.plus.excel.properties;

import io.github.guanxiangkai.web.plus.excel.enums.ExcelStyleType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Office 模块配置属性（Record）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "web-plus.excel")
public record OfficeProperties(
        ExcelStyleType defaultStyle,
        Integer batchSize,
        Integer maxImportRows,
        String tempDir
) {
    public OfficeProperties {
        if (defaultStyle == null) defaultStyle = ExcelStyleType.DEFAULT;
        if (batchSize == null || batchSize <= 0) batchSize = 1000;
        if (maxImportRows == null || maxImportRows <= 0) maxImportRows = 100000;
        if (tempDir == null || tempDir.isBlank()) tempDir = System.getProperty("java.io.tmpdir");
    }

    public static OfficeProperties defaults() {
        return new OfficeProperties(null, null, null, null);
    }
}

