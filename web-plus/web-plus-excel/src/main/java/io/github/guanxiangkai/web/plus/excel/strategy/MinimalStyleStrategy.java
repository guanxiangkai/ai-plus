package io.github.guanxiangkai.web.plus.excel.strategy;

import cn.idev.excel.write.handler.SheetWriteHandler;
import cn.idev.excel.write.handler.WriteHandler;
import cn.idev.excel.write.metadata.holder.WriteSheetHolder;
import cn.idev.excel.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.ss.usermodel.*;

import java.util.List;

/**
 * 简约样式策略 — 最小化样式，仅底部边框
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class MinimalStyleStrategy implements ExcelStyleStrategy {

    public static final MinimalStyleStrategy INSTANCE = new MinimalStyleStrategy();

    private static final List<WriteHandler> HANDLERS = List.of(new MinimalHeaderHandler());

    private MinimalStyleStrategy() {
    }

    @Override
    public List<WriteHandler> getWriteHandlers() {
        return HANDLERS;
    }

    @Override
    public String getName() {
        return "MINIMAL";
    }

    private static final class MinimalHeaderHandler implements SheetWriteHandler {

        @Override
        public void afterSheetCreate(WriteWorkbookHolder workbookHolder, WriteSheetHolder sheetHolder) {
            Workbook workbook = workbookHolder.getWorkbook();
            Sheet sheet = sheetHolder.getSheet();
            sheet.setDefaultColumnWidth(15);
            applyMinimalStyle(workbook, sheet);
        }

        private void applyMinimalStyle(Workbook workbook, Sheet sheet) {
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return;
            CellStyle style = createMinimalStyle(workbook);
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) cell.setCellStyle(style);
            }
        }

        private CellStyle createMinimalStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setBorderBottom(BorderStyle.MEDIUM);
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 11);
            style.setFont(font);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }
    }
}

