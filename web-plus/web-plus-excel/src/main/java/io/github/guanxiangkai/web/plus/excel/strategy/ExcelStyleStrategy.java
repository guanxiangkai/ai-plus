package io.github.guanxiangkai.web.plus.excel.strategy;

import cn.idev.excel.write.handler.WriteHandler;

import java.util.List;

/**
 * Excel 样式策略接口（Sealed Interface）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public sealed interface ExcelStyleStrategy
        permits DefaultStyleStrategy, ProfessionalStyleStrategy, MinimalStyleStrategy, ColorfulStyleStrategy, PrintStyleStrategy {

    List<WriteHandler> getWriteHandlers();

    String getName();
}

