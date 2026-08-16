package io.github.guanxiangkai.web.plus.excel.core;

import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import cn.idev.excel.write.builder.ExcelWriterBuilder;
import cn.idev.excel.write.handler.WriteHandler;
import cn.idev.excel.write.metadata.WriteSheet;
import io.github.guanxiangkai.web.plus.excel.enums.ExcelFileType;
import io.github.guanxiangkai.web.plus.excel.model.ExportContext;
import io.github.guanxiangkai.web.plus.excel.model.ImportResult;
import io.github.guanxiangkai.web.plus.excel.model.ValidationResult;
import io.github.guanxiangkai.web.plus.excel.properties.OfficeProperties;
import io.github.guanxiangkai.web.plus.excel.strategy.ExcelStyleStrategy;
import io.github.guanxiangkai.web.plus.excel.strategy.ExcelStyleStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Excel 操作默认实现（WebFlux 响应式版本）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public final class DefaultExcelOperations implements ExcelOperations {

    private final ExcelStyleStrategyFactory styleStrategyFactory;
    private final int maxImportRows;

    public DefaultExcelOperations(ExcelStyleStrategyFactory styleStrategyFactory) {
        this(styleStrategyFactory, OfficeProperties.defaults().maxImportRows());
    }

    public DefaultExcelOperations(ExcelStyleStrategyFactory styleStrategyFactory, int maxImportRows) {
        this.styleStrategyFactory = Objects.requireNonNull(styleStrategyFactory, "样式策略工厂不能为空");
        if (maxImportRows <= 0) {
            throw new IllegalArgumentException("最大导入行数必须大于 0");
        }
        this.maxImportRows = maxImportRows;
    }

    @Override
    public <T> byte[] export(ExportContext<T> context) {
        Objects.requireNonNull(context, "导出上下文不能为空");
        Objects.requireNonNull(context.dataClass(), "数据类型不能为空");
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            exportToStream(outputStream, context);
            log.info("Excel 导出成功: records={}", context.data().size());
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Excel 导出失败: exception={}", e.getClass().getSimpleName());
            throw new ExcelException("Excel 导出失败", e);
        }
    }

    @Override
    public <T> void exportToStream(OutputStream outputStream, ExportContext<T> context) {
        Objects.requireNonNull(outputStream, "输出流不能为空");
        Objects.requireNonNull(context, "导出上下文不能为空");
        ExcelStyleStrategy styleStrategy = styleStrategyFactory.getStrategy(context.styleType());
        List<WriteHandler> handlers = styleStrategy.getWriteHandlers();
        ExcelWriterBuilder writerBuilder = FastExcel.write(outputStream, context.dataClass())
                .autoCloseStream(Boolean.FALSE);
        handlers.forEach(writerBuilder::registerWriteHandler);
        try (ExcelWriter writer = writerBuilder.build()) {
            WriteSheet sheet = FastExcel.writerSheet(context.sheetName()).build();
            writer.write(context.data(), sheet);
        }
    }

    @Override
    public <T> ImportResult<T> importExcel(MultipartFile file, Class<T> dataClass) {
        ValidationResult validation = validate(file);
        if (validation.isFailed()) {
            return ImportResult.failure(List.of(ImportResult.ImportError.of(0, null, validation.message())));
        }
        try {
            List<T> data;
            try (InputStream is = file.getInputStream()) {
                data = importFromStream(is, dataClass);
            }
            log.info("Excel 导入成功: records={}", data.size());
            return ImportResult.success(data);
        } catch (ExcelException e) {
            log.warn("Excel 导入被拒绝: exception={}", e.getClass().getSimpleName());
            return ImportResult.failure(List.of(
                    ImportResult.ImportError.of(maxImportRows + 1, null, e.getMessage())
            ));
        } catch (IOException e) {
            log.error("Excel 导入失败: exception={}", e.getClass().getSimpleName());
            return ImportResult.failure(List.of(ImportResult.ImportError.of(0, null, "文件读取失败")));
        }
    }

    @Override
    public <T> List<T> importFromStream(InputStream inputStream, Class<T> dataClass) {
        Objects.requireNonNull(inputStream, "输入流不能为空");
        Objects.requireNonNull(dataClass, "数据类型不能为空");
        List<T> dataList = new ArrayList<>();
        FastExcel.read(inputStream, dataClass, this.<T>rowLimitListener(
                (T data, AnalysisContext context) -> dataList.add(data),
                context -> log.debug("Excel 解析完成，共 {} 条数据", dataList.size())
        )).sheet().doRead();
        return List.copyOf(dataList);
    }

    @Override
    public <T> void importStream(MultipartFile file, Class<T> dataClass, Consumer<T> consumer) {
        ValidationResult validation = validate(file);
        if (validation.isFailed()) throw new ExcelException(validation.message());
        try (InputStream is = file.getInputStream()) {
            FastExcel.read(is, dataClass, this.<T>rowLimitListener(
                    (T data, AnalysisContext context) -> consumer.accept(data),
                    context -> log.debug("Excel 流式解析完成")
            )).sheet().doRead();
            log.info("Excel 流式导入成功");
        } catch (IOException e) {
            log.error("Excel 流式导入失败: exception={}", e.getClass().getSimpleName());
            throw new ExcelException("Excel 流式导入失败", e);
        }
    }

    @Override
    public <T> void importBatch(MultipartFile file, Class<T> dataClass, int batchSize, Consumer<List<T>> handler) {
        ValidationResult validation = validate(file);
        if (validation.isFailed()) throw new ExcelException(validation.message());
        if (batchSize <= 0) throw new IllegalArgumentException("批量大小必须大于 0");
        try (InputStream is = file.getInputStream()) {
            FastExcel.read(is, dataClass, new BatchDataListener<>(batchSize, handler, maxImportRows))
                    .sheet().doRead();
            log.info("Excel 批量导入成功: batchSize={}", batchSize);
        } catch (IOException e) {
            log.error("Excel 批量导入失败: exception={}", e.getClass().getSimpleName());
            throw new ExcelException("Excel 批量导入失败", e);
        }
    }

    @Override
    public ValidationResult validate(MultipartFile file) {
        if (file == null || file.isEmpty()) return ValidationResult.failure("文件不能为空");
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) return ValidationResult.failure("文件名不能为空");
        ExcelFileType fileType = ExcelFileType.fromFileName(fileName);
        if (fileType == ExcelFileType.CSV) return ValidationResult.failure("暂不支持 CSV 格式");
        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls"))
            return ValidationResult.failure("文件格式不正确，仅支持 .xlsx 和 .xls");
        return ValidationResult.success(fileName, file.getSize(), fileType.getCode());
    }

    private String importRowLimitExceededMessage() {
        return "Excel 导入行数超出限制，最大支持 " + maxImportRows + " 行";
    }

    private <T> AnalysisEventListener<T> rowLimitListener(BiConsumer<T, AnalysisContext> rowHandler,
                                                          Consumer<AnalysisContext> completionHandler) {
        return new AnalysisEventListener<>() {
            private int rowCount;

            @Override
            public void invoke(T data, AnalysisContext context) {
                rowCount++;
                if (rowCount > maxImportRows) {
                    throw new ExcelException(importRowLimitExceededMessage());
                }
                rowHandler.accept(data, context);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                completionHandler.accept(context);
            }
        };
    }

    private static final class BatchDataListener<T> extends AnalysisEventListener<T> {
        private final int batchSize;
        private final Consumer<List<T>> handler;
        private final List<T> buffer;
        private final int maxImportRows;
        private int rowCount;

        BatchDataListener(int batchSize, Consumer<List<T>> handler, int maxImportRows) {
            this.batchSize = batchSize;
            this.handler = handler;
            this.buffer = new ArrayList<>(batchSize);
            this.maxImportRows = maxImportRows;
        }

        @Override
        public void invoke(T data, AnalysisContext context) {
            rowCount++;
            if (rowCount > maxImportRows) {
                throw new ExcelException("Excel 导入行数超出限制，最大支持 " + maxImportRows + " 行");
            }
            buffer.add(data);
            if (buffer.size() >= batchSize) {
                handler.accept(List.copyOf(buffer));
                buffer.clear();
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            if (!buffer.isEmpty()) {
                handler.accept(List.copyOf(buffer));
                buffer.clear();
            }
            log.debug("Excel 批量解析完成");
        }
    }
}
