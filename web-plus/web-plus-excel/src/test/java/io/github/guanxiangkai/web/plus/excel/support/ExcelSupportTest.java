package io.github.guanxiangkai.web.plus.excel.support;

import cn.idev.excel.FastExcel;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.guanxiangkai.web.plus.excel.core.ExcelException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelSupportTest {

    @Test
    void readSync_rejectsFilesThatExceedConfiguredLimit() {
        assertThatThrownBy(() -> ExcelSupport.readSync(new ByteArrayInputStream(excelBytes(3)), TestRow.class, 2))
                .isInstanceOf(ExcelException.class)
                .hasMessageContaining("最大支持 2 行");
    }

    @Test
    void readBatch_rejectsFilesThatExceedConfiguredLimit() {
        AtomicInteger consumed = new AtomicInteger();

        assertThatThrownBy(() -> ExcelSupport.readBatch(
                new ByteArrayInputStream(excelBytes(3)),
                TestRow.class,
                2,
                2,
                batch -> consumed.addAndGet(batch.size())
        ).block())
                .isInstanceOf(ExcelException.class)
                .hasMessageContaining("最大支持 2 行");
        assertThat(consumed).hasValue(2);
    }

    @Test
    void readSync_acceptsFilesWithinConfiguredLimit() {
        List<TestRow> rows = ExcelSupport.readSync(new ByteArrayInputStream(excelBytes(2)), TestRow.class, 2);

        assertThat(rows).extracting(TestRow::getName)
                .containsExactly("row-0", "row-1");
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
