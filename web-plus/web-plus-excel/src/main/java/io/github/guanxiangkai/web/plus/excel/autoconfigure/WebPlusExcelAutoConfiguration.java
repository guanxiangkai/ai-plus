package io.github.guanxiangkai.web.plus.excel.autoconfigure;

import io.github.guanxiangkai.web.plus.excel.core.DefaultExcelOperations;
import io.github.guanxiangkai.web.plus.excel.core.ExcelOperations;
import io.github.guanxiangkai.web.plus.excel.properties.OfficeProperties;
import io.github.guanxiangkai.web.plus.excel.service.ExcelService;
import io.github.guanxiangkai.web.plus.excel.strategy.ExcelStyleStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * web-plus-excel 自动装配
 * <p>
 * 需要 FastExcel（{@code cn.idev.excel.FastExcel}）在类路径中才会生效。
 * 注册 {@link ExcelStyleStrategyFactory}、{@link ExcelOperations} 与
 * {@link ExcelService} 三个核心 Bean；均支持通过 {@code @ConditionalOnMissingBean} 被业务侧覆盖。
 * </p>
 *
 * <pre>
 * # application.yml 可选配置
 * web-plus:
 *   excel:
 *     default-style: PROFESSIONAL   # DEFAULT / PROFESSIONAL / MINIMAL / COLORFUL / PRINT
 *     batch-size: 500               # 批量导入每批条数，默认 1000
 *     max-import-rows: 50000        # 最大导入行数，默认 100000
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "cn.idev.excel.FastExcel")
@EnableConfigurationProperties(OfficeProperties.class)
public class WebPlusExcelAutoConfiguration {

    public WebPlusExcelAutoConfiguration() {
        log.info("[web-plus] Excel 模块已启用（FastExcel）");
    }

    /**
     * 样式策略工厂 —— 单例，管理 5 种内置样式策略。
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelStyleStrategyFactory excelStyleStrategyFactory() {
        return new ExcelStyleStrategyFactory();
    }

    /**
     * Excel 操作核心实现 —— 封装 FastExcel 导入/导出 API。
     */
    @Bean
    @ConditionalOnMissingBean(ExcelOperations.class)
    public ExcelOperations excelOperations(ExcelStyleStrategyFactory styleStrategyFactory,
                                           OfficeProperties properties) {
        return new DefaultExcelOperations(styleStrategyFactory, properties.maxImportRows());
    }

    /**
     * Excel 门面服务 —— 对业务层提供语义化的导入/导出方法。
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelService excelService(ExcelOperations excelOperations, OfficeProperties properties) {
        log.debug("[web-plus] Excel 服务配置: defaultStyle={}, batchSize={}, maxImportRows={}",
                properties.defaultStyle(), properties.batchSize(), properties.maxImportRows());
        return new ExcelService(excelOperations, properties);
    }
}
