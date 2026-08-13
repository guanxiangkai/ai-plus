package io.github.guanxiangkai.web.plus.excel.aot;

import io.github.guanxiangkai.web.plus.excel.core.DefaultExcelOperations;
import io.github.guanxiangkai.web.plus.excel.model.ExportContext;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelNativeConfigurationTest {

    @Test
    void registrarShouldRegisterExcelPublicBoundaries() {
        RuntimeHints hints = new RuntimeHints();

        new ExcelNativeConfiguration.Registrar().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(DefaultExcelOperations.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(ExportContext.class).test(hints)).isTrue();
    }
}
