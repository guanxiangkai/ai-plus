package io.github.guanxiangkai.web.plus.excel.strategy;

import cn.idev.excel.write.handler.WriteHandler;
import cn.idev.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;

import java.util.List;

/**
 * 默认样式策略 — 仅自动列宽，无特殊样式
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class DefaultStyleStrategy implements ExcelStyleStrategy {

    public static final DefaultStyleStrategy INSTANCE = new DefaultStyleStrategy();

    private static final List<WriteHandler> HANDLERS = List.of(
            new LongestMatchColumnWidthStyleStrategy()
    );

    private DefaultStyleStrategy() {
    }

    @Override
    public List<WriteHandler> getWriteHandlers() {
        return HANDLERS;
    }

    @Override
    public String getName() {
        return "DEFAULT";
    }
}

