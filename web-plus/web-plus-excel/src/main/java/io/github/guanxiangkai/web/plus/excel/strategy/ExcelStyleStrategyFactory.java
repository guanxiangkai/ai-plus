package io.github.guanxiangkai.web.plus.excel.strategy;

import io.github.guanxiangkai.web.plus.excel.enums.ExcelStyleType;

import java.util.Map;

/**
 * Excel 样式策略工厂
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class ExcelStyleStrategyFactory {

    private static final Map<ExcelStyleType, ExcelStyleStrategy> STRATEGY_MAP = Map.of(
            ExcelStyleType.DEFAULT, DefaultStyleStrategy.INSTANCE,
            ExcelStyleType.PROFESSIONAL, ProfessionalStyleStrategy.INSTANCE,
            ExcelStyleType.MINIMAL, MinimalStyleStrategy.INSTANCE,
            ExcelStyleType.COLORFUL, ColorfulStyleStrategy.INSTANCE,
            ExcelStyleType.PRINT, PrintStyleStrategy.INSTANCE
    );

    public ExcelStyleStrategy getStrategy(ExcelStyleType styleType) {
        if (styleType == null) return DefaultStyleStrategy.INSTANCE;
        return switch (styleType) {
            case DEFAULT -> DefaultStyleStrategy.INSTANCE;
            case PROFESSIONAL -> ProfessionalStyleStrategy.INSTANCE;
            case MINIMAL -> MinimalStyleStrategy.INSTANCE;
            case COLORFUL -> ColorfulStyleStrategy.INSTANCE;
            case PRINT -> PrintStyleStrategy.INSTANCE;
        };
    }

    public ExcelStyleStrategy getStrategyByCode(String code) {
        return getStrategy(ExcelStyleType.fromCode(code));
    }
}

