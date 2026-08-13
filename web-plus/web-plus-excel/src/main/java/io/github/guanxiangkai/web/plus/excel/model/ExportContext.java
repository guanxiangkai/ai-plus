package io.github.guanxiangkai.web.plus.excel.model;

import io.github.guanxiangkai.web.plus.excel.enums.ExcelFileType;
import io.github.guanxiangkai.web.plus.excel.enums.ExcelStyleType;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Excel 导出上下文（Record）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@RegisterReflectionForBinding
public record ExportContext<T>(
        String fileName,
        String sheetName,
        Class<T> dataClass,
        List<T> data,
        ExcelStyleType styleType,
        ExcelFileType fileType
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ExportContext {
        if (fileName == null || fileName.isBlank()) fileName = "export";
        if (sheetName == null || sheetName.isBlank()) sheetName = "Sheet1";
        if (styleType == null) styleType = ExcelStyleType.DEFAULT;
        if (fileType == null) fileType = ExcelFileType.XLSX;
        if (data == null) data = List.of();
    }

    public static <T> ExportContext<T> of(String fileName, Class<T> dataClass, List<T> data) {
        return new ExportContext<>(fileName, "Sheet1", dataClass, data,
                ExcelStyleType.DEFAULT, ExcelFileType.XLSX);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public String fullFileName() {
        return fileName + fileType.getExtension();
    }

    public static final class Builder<T> {
        private String fileName = "export";
        private String sheetName = "Sheet1";
        private Class<T> dataClass;
        private List<T> data = List.of();
        private ExcelStyleType styleType = ExcelStyleType.DEFAULT;
        private ExcelFileType fileType = ExcelFileType.XLSX;

        public Builder<T> fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder<T> sheetName(String sheetName) {
            this.sheetName = sheetName;
            return this;
        }

        public Builder<T> dataClass(Class<T> dataClass) {
            this.dataClass = dataClass;
            return this;
        }

        public Builder<T> data(List<T> data) {
            this.data = data;
            return this;
        }

        public Builder<T> styleType(ExcelStyleType styleType) {
            this.styleType = styleType;
            return this;
        }

        public Builder<T> fileType(ExcelFileType fileType) {
            this.fileType = fileType;
            return this;
        }

        public Builder<T> professional() {
            this.styleType = ExcelStyleType.PROFESSIONAL;
            return this;
        }

        public Builder<T> colorful() {
            this.styleType = ExcelStyleType.COLORFUL;
            return this;
        }

        public Builder<T> minimal() {
            this.styleType = ExcelStyleType.MINIMAL;
            return this;
        }

        public ExportContext<T> build() {
            return new ExportContext<>(fileName, sheetName, dataClass, data, styleType, fileType);
        }
    }
}

