package io.github.guanxiangkai.web.plus.excel.core;

import io.github.guanxiangkai.web.plus.excel.model.ExportContext;
import io.github.guanxiangkai.web.plus.excel.model.ImportResult;
import io.github.guanxiangkai.web.plus.excel.model.ValidationResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Excel 操作核心接口（Sealed Interface）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public sealed interface ExcelOperations permits DefaultExcelOperations {

    <T> byte[] export(ExportContext<T> context);

    <T> void exportToStream(OutputStream outputStream, ExportContext<T> context);

    <T> ImportResult<T> importExcel(MultipartFile file, Class<T> dataClass);

    <T> List<T> importFromStream(InputStream inputStream, Class<T> dataClass);

    <T> void importStream(MultipartFile file, Class<T> dataClass, Consumer<T> consumer);

    <T> void importBatch(MultipartFile file, Class<T> dataClass, int batchSize, Consumer<List<T>> handler);

    ValidationResult validate(MultipartFile file);
}

