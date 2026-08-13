package io.github.guanxiangkai.web.plus.excel.strategy;

import cn.idev.excel.write.handler.SheetWriteHandler;
import cn.idev.excel.write.handler.WriteHandler;
import cn.idev.excel.write.metadata.holder.WriteSheetHolder;
import cn.idev.excel.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.ss.usermodel.*;

import java.util.List;

/**
 * 打印样式策略 — 适合打印输出，黑白配色，清晰边框
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class PrintStyleStrategy implements ExcelStyleStrategy {

    public static final PrintStyleStrategy INSTANCE = new PrintStyleStrategy();

    private static final List<WriteHandler> HANDLERS = List.of(new PrintStyleHandler());

    private PrintStyleStrategy() {
    }

    @Override
    public List<WriteHandler> getWriteHandlers() {
        return HANDLERS;
    }

    @Override
    public String getName() {
        return "PRINT";
    }

    private static final class PrintStyleHandler implements SheetWriteHandler {

        @Override
        public void afterSheetCreate(WriteWorkbookHolder workbookHolder, WriteSheetHolder sheetHolder) {
            Workbook workbook = workbookHolder.getWorkbook();
            Sheet sheet = sheetHolder.getSheet();
            sheet.setDefaultColumnWidth(15);
            sheet.createFreezePane(0, 1);
            sheet.setRepeatingRows(org.apache.poi.ss.util.CellRangeAddress.valueOf("1:1"));
            applyPrintStyle(workbook, sheet);
        }

        private void applyPrintStyle(Workbook workbook, Sheet sheet) {
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return;
            CellStyle style = createPrintHeaderStyle(workbook);
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) cell.setCellStyle(style);
            }
        }

        private CellStyle createPrintHeaderStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 11);
            style.setFont(font);
            style.setBorderTop(BorderStyle.MEDIUM);
            style.setBorderBottom(BorderStyle.MEDIUM);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }
    }
}

