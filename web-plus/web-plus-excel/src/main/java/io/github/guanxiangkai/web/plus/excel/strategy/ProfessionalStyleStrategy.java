package io.github.guanxiangkai.web.plus.excel.strategy;

import cn.idev.excel.write.handler.SheetWriteHandler;
import cn.idev.excel.write.handler.WriteHandler;
import cn.idev.excel.write.metadata.holder.WriteSheetHolder;
import cn.idev.excel.write.metadata.holder.WriteWorkbookHolder;
import cn.idev.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.apache.poi.ss.usermodel.*;

import java.util.List;

/**
 * 专业样式策略 — 深蓝色表头、冻结首行、白色粗体字
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class ProfessionalStyleStrategy implements ExcelStyleStrategy {

    public static final ProfessionalStyleStrategy INSTANCE = new ProfessionalStyleStrategy();

    private static final List<WriteHandler> HANDLERS = List.of(
            new LongestMatchColumnWidthStyleStrategy(),
            new ProfessionalHeaderHandler()
    );

    private ProfessionalStyleStrategy() {
    }

    @Override
    public List<WriteHandler> getWriteHandlers() {
        return HANDLERS;
    }

    @Override
    public String getName() {
        return "PROFESSIONAL";
    }

    private static final class ProfessionalHeaderHandler implements SheetWriteHandler {

        @Override
        public void afterSheetCreate(WriteWorkbookHolder workbookHolder, WriteSheetHolder sheetHolder) {
            Workbook workbook = workbookHolder.getWorkbook();
            Sheet sheet = sheetHolder.getSheet();
            sheet.setDefaultColumnWidth(18);
            sheet.createFreezePane(0, 1);
            applyHeaderStyle(workbook, sheet);
        }

        private void applyHeaderStyle(Workbook workbook, Sheet sheet) {
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return;
            CellStyle style = createHeaderStyle(workbook);
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) cell.setCellStyle(style);
            }
        }

        private CellStyle createHeaderStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setColor(IndexedColors.WHITE.getIndex());
            font.setBold(true);
            font.setFontHeightInPoints((short) 12);
            style.setFont(font);
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

