package io.github.guanxiangkai.web.plus.excel.service;

import io.github.guanxiangkai.web.plus.excel.core.ExcelOperations;
import io.github.guanxiangkai.web.plus.excel.enums.ExcelStyleType;
import io.github.guanxiangkai.web.plus.excel.model.ExportContext;
import io.github.guanxiangkai.web.plus.excel.model.ImportResult;
import io.github.guanxiangkai.web.plus.excel.model.ValidationResult;
import io.github.guanxiangkai.web.plus.excel.properties.OfficeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Excel 服务（门面模式 · WebFlux 响应式版本）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ExcelService {

    private final ExcelOperations excelOperations;
    private final OfficeProperties properties;

    public <T> byte[] export(String fileName, Class<T> dataClass, List<T> data) {
        return export(fileName, "Sheet1", dataClass, data, properties.defaultStyle());
    }

    public <T> byte[] export(String fileName, String sheetName, Class<T> dataClass, List<T> data) {
        return export(fileName, sheetName, dataClass, data, properties.defaultStyle());
    }

    public <T> byte[] export(String fileName, String sheetName,
                             Class<T> dataClass, List<T> data, ExcelStyleType styleType) {
        ExportContext<T> context = ExportContext.<T>builder()
                .fileName(fileName).sheetName(sheetName)
                .dataClass(dataClass).data(data).styleType(styleType).build();
        return excelOperations.export(context);
    }

    public <T> byte[] exportProfessional(String fileName, Class<T> dataClass, List<T> data) {
        return export(fileName, "Sheet1", dataClass, data, ExcelStyleType.PROFESSIONAL);
    }

    public <T> byte[] exportColorful(String fileName, Class<T> dataClass, List<T> data) {
        return export(fileName, "Sheet1", dataClass, data, ExcelStyleType.COLORFUL);
    }

    public <T> ImportResult<T> importExcel(MultipartFile file, Class<T> dataClass) {
        return excelOperations.importExcel(file, dataClass);
    }

    public <T> List<T> importFromStream(InputStream inputStream, Class<T> dataClass) {
        return excelOperations.importFromStream(inputStream, dataClass);
    }

    public <T> void importStream(MultipartFile file, Class<T> dataClass, Consumer<T> consumer) {
        excelOperations.importStream(file, dataClass, consumer);
    }

    public <T> void importBatch(MultipartFile file, Class<T> dataClass, Consumer<List<T>> handler) {
        excelOperations.importBatch(file, dataClass, properties.batchSize(), handler);
    }

    public <T> void importBatch(MultipartFile file, Class<T> dataClass, int batchSize, Consumer<List<T>> handler) {
        excelOperations.importBatch(file, dataClass, batchSize, handler);
    }

    public ValidationResult validate(MultipartFile file) {
        return excelOperations.validate(file);
    }

    public boolean isValidExcelFile(MultipartFile file) {
        return excelOperations.validate(file).valid();
    }

    public <T> ExportBuilder<T> exportBuilder() {
        return new ExportBuilder<>(this);
    }

    public static final class ExportBuilder<T> {
        private final ExcelService service;
        private String fileName = "export";
        private String sheetName = "Sheet1";
        private Class<T> dataClass;
        private List<T> data = List.of();
        private ExcelStyleType styleType;

        private ExportBuilder(ExcelService service) {
            this.service = service;
            this.styleType = service.properties.defaultStyle();
        }

        public ExportBuilder<T> fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public ExportBuilder<T> sheetName(String sheetName) {
            this.sheetName = sheetName;
            return this;
        }

        public ExportBuilder<T> dataClass(Class<T> dataClass) {
            this.dataClass = dataClass;
            return this;
        }

        public ExportBuilder<T> data(List<T> data) {
            this.data = data;
            return this;
        }

        public ExportBuilder<T> styleType(ExcelStyleType styleType) {
            this.styleType = styleType;
            return this;
        }

        public ExportBuilder<T> professional() {
            return styleType(ExcelStyleType.PROFESSIONAL);
        }

        public ExportBuilder<T> colorful() {
            return styleType(ExcelStyleType.COLORFUL);
        }

        public ExportBuilder<T> minimal() {
            return styleType(ExcelStyleType.MINIMAL);
        }

        public byte[] execute() {
            return service.export(fileName, sheetName, dataClass, data, styleType);
        }
    }
}

