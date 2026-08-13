package io.github.guanxiangkai.web.plus.excel.strategy;

import cn.idev.excel.write.handler.RowWriteHandler;
import cn.idev.excel.write.handler.WriteHandler;
import cn.idev.excel.write.metadata.holder.WriteSheetHolder;
import cn.idev.excel.write.metadata.holder.WriteTableHolder;
import cn.idev.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.apache.poi.ss.usermodel.*;

import java.util.List;

/**
 * 彩色样式策略 — 交替行颜色，增强可读性
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class ColorfulStyleStrategy implements ExcelStyleStrategy {

    public static final ColorfulStyleStrategy INSTANCE = new ColorfulStyleStrategy();

    private static final List<WriteHandler> HANDLERS = List.of(
            new LongestMatchColumnWidthStyleStrategy(),
            new ColorfulRowHandler()
    );

    private ColorfulStyleStrategy() {
    }

    @Override
    public List<WriteHandler> getWriteHandlers() {
        return HANDLERS;
    }

    @Override
    public String getName() {
        return "COLORFUL";
    }

    private static final class ColorfulRowHandler implements RowWriteHandler {

        private static final short HEADER_COLOR = IndexedColors.ROYAL_BLUE.getIndex();
        private static final short EVEN_ROW_COLOR = IndexedColors.PALE_BLUE.getIndex();
        private static final short ODD_ROW_COLOR = IndexedColors.WHITE.getIndex();

        @Override
        public void afterRowDispose(WriteSheetHolder sheetHolder, WriteTableHolder tableHolder,
                                    Row row, Integer relativeRowIndex, Boolean isHead) {
            if (row == null) return;
            Workbook workbook = sheetHolder.getSheet().getWorkbook();
            CellStyle style = createRowStyle(workbook, relativeRowIndex, isHead);
            for (int i = 0; i < row.getLastCellNum(); i++) {
                Cell cell = row.getCell(i);
                if (cell != null) cell.setCellStyle(style);
            }
        }

        private CellStyle createRowStyle(Workbook workbook, Integer rowIndex, Boolean isHead) {
            CellStyle style = workbook.createCellStyle();
            if (Boolean.TRUE.equals(isHead)) {
                style.setFillForegroundColor(HEADER_COLOR);
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                Font font = workbook.createFont();
                font.setColor(IndexedColors.WHITE.getIndex());
                font.setBold(true);
                font.setFontHeightInPoints((short) 12);
                style.setFont(font);
            } else {
                short color = (rowIndex != null && rowIndex % 2 == 0) ? EVEN_ROW_COLOR : ODD_ROW_COLOR;
                style.setFillForegroundColor(color);
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }
    }
}

