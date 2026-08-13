package io.github.guanxiangkai.web.plus.excel.support;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import io.github.guanxiangkai.web.plus.excel.core.ExcelException;
import io.github.guanxiangkai.web.plus.excel.properties.OfficeProperties;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * FastExcel 工具类 —— 基于 cn.idev.excel:fastexcel 提供 Excel 导入/导出能力
 * <p>
 * 与 WebFlux 集成，所有 IO 操作均在 {@link Schedulers#boundedElastic()} 线程执行，
 * 避免阻塞 Reactor 事件循环线程。
 * </p>
 *
 * <pre>
 * // 响应式导出示例（Controller 中）：
 * ExcelSupport.export(UserVO.class, "用户列表", userList)
 *     .flatMap(bytes -> ServerResponse.ok()
 *         .contentType(MediaType.APPLICATION_OCTET_STREAM)
 *         .bodyValue(bytes));
 *
 * // 响应式导入示例：
 * ExcelSupport.read(inputStream, UserImportDTO.class)
 *     .flatMap(list -> userService.batchCreate(list));
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public final class ExcelSupport {

    private ExcelSupport() {
    }

    // ── 导出 ─────────────────────────────────────────────────

    /**
     * 导出 Excel 为 byte[]（响应式）
     *
     * @param clazz     导出数据类（字段上添加 &#64;ExcelProperty 注解）
     * @param sheetName Sheet 名称
     * @param data      导出数据
     * @param <T>       数据类型
     * @return Excel 文件字节数组的 Mono
     */
    public static <T> Mono<byte[]> export(Class<T> clazz, String sheetName, List<T> data) {
        return Mono.fromCallable(() -> {
                    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                        FastExcel.write(bos, clazz)
                                .sheet(sheetName)
                                .doWrite(data);
                        return bos.toByteArray();
                    }
                }).subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("[excel] 导出失败: {}", e.getMessage(), e));
    }

    /**
     * 自定义导出（可通过 sheetConfigurator 设置冻结行/列、列宽等）
     */
    public static <T> Mono<byte[]> exportCustom(Class<T> clazz,
                                                String sheetName,
                                                List<T> data,
                                                Consumer<cn.idev.excel.write.builder.ExcelWriterSheetBuilder> sheetConfigurator) {
        return Mono.fromCallable(() -> {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                var sheetBuilder = FastExcel.write(bos, clazz).sheet(sheetName);
                sheetConfigurator.accept(sheetBuilder);
                sheetBuilder.doWrite(data);
                return bos.toByteArray();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 多 Sheet 导出
     *
     * @param clazz  数据类型
     * @param sheets Sheet 名称 → 数据 的 Map（有序）
     */
    public static <T> Mono<byte[]> exportMultiSheet(Class<T> clazz,
                                                    java.util.LinkedHashMap<String, List<T>> sheets) {
        return Mono.fromCallable(() -> {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 var writer = FastExcel.write(bos, clazz).build()) {
                int sheetNo = 0;
                for (var entry : sheets.entrySet()) {
                    var sheet = FastExcel.writerSheet(sheetNo++, entry.getKey()).build();
                    writer.write(entry.getValue(), sheet);
                }
                return bos.toByteArray();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── 导入 ─────────────────────────────────────────────────

    /**
     * 响应式读取 Excel
     *
     * @param inputStream Excel 输入流
     * @param clazz       目标数据类型
     * @param <T>         数据类型
     * @return 解析后数据列表的 Mono
     */
    public static <T> Mono<List<T>> read(InputStream inputStream, Class<T> clazz) {
        return read(inputStream, clazz, OfficeProperties.defaults().maxImportRows());
    }

    public static <T> Mono<List<T>> read(InputStream inputStream, Class<T> clazz, int maxImportRows) {
        return Mono.fromCallable(() -> readSync(inputStream, clazz, maxImportRows))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("[excel] 导入失败: {}", e.getMessage(), e));
    }

    /**
     * 同步读取 Excel（用于非响应式场景，务必在 boundedElastic 线程调用）
     */
    public static <T> List<T> readSync(InputStream inputStream, Class<T> clazz) {
        return readSync(inputStream, clazz, OfficeProperties.defaults().maxImportRows());
    }

    public static <T> List<T> readSync(InputStream inputStream, Class<T> clazz, int maxImportRows) {
        List<T> result = new ArrayList<>();
        FastExcel.read(inputStream, clazz, ExcelSupport.<T>rowLimitListener(
                maxImportRows,
                (T data, AnalysisContext ctx) -> result.add(data),
                ctx -> log.debug("[excel] 读取完成，共 {} 条", result.size())
        )).sheet().doRead();
        return result;
    }

    /**
     * 流式读取（每批次回调，适合大文件导入防 OOM）
     *
     * @param inputStream  输入流
     * @param clazz        数据类型
     * @param batchSize    每批处理量
     * @param batchHandler 批次处理器（异步，在 boundedElastic 线程调用）
     */
    public static <T> Mono<Void> readBatch(InputStream inputStream, Class<T> clazz,
                                           int batchSize, Consumer<List<T>> batchHandler) {
        return readBatch(inputStream, clazz, batchSize, OfficeProperties.defaults().maxImportRows(), batchHandler);
    }

    public static <T> Mono<Void> readBatch(InputStream inputStream, Class<T> clazz,
                                           int batchSize, int maxImportRows,
                                           Consumer<List<T>> batchHandler) {
        return Mono.fromRunnable(() -> {
            List<T> batch = new ArrayList<>(batchSize);
            FastExcel.read(inputStream, clazz, ExcelSupport.<T>rowLimitListener(
                    maxImportRows,
                    (T data, AnalysisContext ctx) -> {
                        batch.add(data);
                        if (batch.size() >= batchSize) {
                            batchHandler.accept(new ArrayList<>(batch));
                            batch.clear();
                        }
                    },
                    ctx -> {
                        if (!batch.isEmpty()) batchHandler.accept(batch);
                    }
            )).sheet().doRead();
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private static <T> AnalysisEventListener<T> rowLimitListener(int maxImportRows,
                                                                 BiConsumer<T, AnalysisContext> rowHandler,
                                                                 Consumer<AnalysisContext> completionHandler) {
        if (maxImportRows <= 0) {
            throw new IllegalArgumentException("最大导入行数必须大于 0");
        }
        return new AnalysisEventListener<>() {
            private int rowCount;

            @Override
            public void invoke(T data, AnalysisContext ctx) {
                rowCount++;
                if (rowCount > maxImportRows) {
                    throw new ExcelException("Excel 导入行数超出限制，最大支持 " + maxImportRows + " 行");
                }
                rowHandler.accept(data, ctx);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext ctx) {
                completionHandler.accept(ctx);
            }
        };
    }
}
