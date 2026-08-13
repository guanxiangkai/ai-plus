package io.github.guanxiangkai.web.plus.excel.converter;

import cn.idev.excel.converters.Converter;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.GlobalConfiguration;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.metadata.property.ExcelContentProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * BigDecimal 转换器
 * <p>读取时智能清洗货币符号、千分符后解析；写入时格式化为 #,##0.00。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class BigDecimalConverter implements Converter<BigDecimal> {

    private static final int DEFAULT_SCALE = 2;
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    @Override
    public Class<BigDecimal> supportJavaTypeKey() {
        return BigDecimal.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public BigDecimal convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
                                        GlobalConfiguration globalConfiguration) {
        String value = cellData.getStringValue();
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim()
                .replace("¥", "").replace("$", "").replace("€", "")
                .replace(",", "").replace(" ", "").replace("%", "");
        try {
            return new BigDecimal(cleaned).setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法解析数值: " + value, e);
        }
    }

    @Override
    public WriteCellData<?> convertToExcelData(BigDecimal value, ExcelContentProperty contentProperty,
                                               GlobalConfiguration globalConfiguration) {
        if (value == null) return new WriteCellData<>("");
        DecimalFormat fmt = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.US));
        return new WriteCellData<>(fmt.format(value));
    }
}
