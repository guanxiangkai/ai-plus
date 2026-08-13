package io.github.guanxiangkai.web.plus.excel.core;

import cn.idev.excel.FastExcel;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.guanxiangkai.web.plus.excel.strategy.ExcelStyleStrategyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultExcelOperationsTest {

    @Test
    void importExcel_returnsFailureWhenRowCountExceedsConfiguredLimit() {
        DefaultExcelOperations operations = new DefaultExcelOperations(new ExcelStyleStrategyFactory(), 2);

        var result = operations.importExcel(multipartFile(3), TestRow.class);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).singleElement()
                .satisfies(error -> {
                    assertThat(error.rowIndex()).isEqualTo(3);
                    assertThat(error.message()).contains("最大支持 2 行");
                });
    }

    @Test
    void importStream_rejectsFilesThatExceedConfiguredLimit() {
        DefaultExcelOperations operations = new DefaultExcelOperations(new ExcelStyleStrategyFactory(), 2);
        AtomicInteger consumed = new AtomicInteger();

        assertThatThrownBy(() -> operations.importStream(multipartFile(3), TestRow.class, row -> consumed.incrementAndGet()))
                .isInstanceOf(ExcelException.class)
                .hasMessageContaining("最大支持 2 行");
        assertThat(consumed).hasValue(2);
    }

    @Test
    void importBatch_rejectsFilesThatExceedConfiguredLimit() {
        DefaultExcelOperations operations = new DefaultExcelOperations(new ExcelStyleStrategyFactory(), 2);

        assertThatThrownBy(() -> operations.importBatch(multipartFile(3), TestRow.class, 2, rows -> {
        }))
                .isInstanceOf(ExcelException.class)
                .hasMessageContaining("最大支持 2 行");
    }

    @Test
    void importFromStream_acceptsFilesWithinConfiguredLimit() {
        DefaultExcelOperations operations = new DefaultExcelOperations(new ExcelStyleStrategyFactory(), 3);

        List<TestRow> rows = operations.importFromStream(
                new java.io.ByteArrayInputStream(excelBytes(3)),
                TestRow.class
        );

        assertThat(rows).extracting(TestRow::getName)
                .containsExactly("row-0", "row-1", "row-2");
    }

    private static MockMultipartFile multipartFile(int rows) {
        return new MockMultipartFile(
                "file",
                "rows.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes(rows)
        );
    }

    private static byte[] excelBytes(int rows) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            FastExcel.write(outputStream, TestRow.class)
                    .sheet("Sheet1")
                    .doWrite(IntStream.range(0, rows)
                            .mapToObj(i -> new TestRow("row-" + i))
                            .toList());
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class TestRow {
        @ExcelProperty("name")
        private String name;

        public TestRow() {
        }

        public TestRow(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
