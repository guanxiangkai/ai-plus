package io.github.guanxiangkai.web.plus.excel.aot;

import io.github.guanxiangkai.web.plus.excel.converter.BigDecimalConverter;
import io.github.guanxiangkai.web.plus.excel.converter.SmartLocalDateConverter;
import io.github.guanxiangkai.web.plus.excel.converter.SmartLocalDateTimeConverter;
import io.github.guanxiangkai.web.plus.excel.core.DefaultExcelOperations;
import io.github.guanxiangkai.web.plus.excel.core.ExcelException;
import io.github.guanxiangkai.web.plus.excel.enums.ExcelFileType;
import io.github.guanxiangkai.web.plus.excel.enums.ExcelOperationType;
import io.github.guanxiangkai.web.plus.excel.enums.ExcelStyleType;
import io.github.guanxiangkai.web.plus.excel.model.ExportContext;
import io.github.guanxiangkai.web.plus.excel.model.ImportResult;
import io.github.guanxiangkai.web.plus.excel.model.ValidationResult;
import io.github.guanxiangkai.web.plus.excel.properties.OfficeProperties;
import io.github.guanxiangkai.web.plus.excel.strategy.ColorfulStyleStrategy;
import io.github.guanxiangkai.web.plus.excel.strategy.DefaultStyleStrategy;
import io.github.guanxiangkai.web.plus.excel.strategy.ExcelStyleStrategyFactory;
import io.github.guanxiangkai.web.plus.excel.strategy.MinimalStyleStrategy;
import io.github.guanxiangkai.web.plus.excel.strategy.PrintStyleStrategy;
import io.github.guanxiangkai.web.plus.excel.strategy.ProfessionalStyleStrategy;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.Set;

/**
 * Web Plus Excel 模块的 GraalVM Native Image 运行时提示。
 *
 * @author guanxiangkai
 * @since 1.0.2
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(ExcelNativeConfiguration.Registrar.class)
public class ExcelNativeConfiguration {

    static final class Registrar implements RuntimeHintsRegistrar {

        private static final Set<Class<?>> REFLECTION_TYPES = Set.of(
                ExcelStyleType.class, ExcelFileType.class, ExcelOperationType.class,
                OfficeProperties.class,
                SmartLocalDateConverter.class, SmartLocalDateTimeConverter.class, BigDecimalConverter.class,
                DefaultExcelOperations.class, ExcelException.class,
                ExportContext.class, ExportContext.Builder.class,
                ValidationResult.class, ImportResult.class, ImportResult.ImportError.class,
                ExcelStyleStrategyFactory.class, DefaultStyleStrategy.class,
                ProfessionalStyleStrategy.class, MinimalStyleStrategy.class,
                ColorfulStyleStrategy.class, PrintStyleStrategy.class
        );

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            REFLECTION_TYPES.forEach(type -> hints.reflection().registerType(type,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS));
            hints.resources().registerPattern("META-INF/spring/*");
        }
    }
}
