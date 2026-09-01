package io.github.guanxiangkai.web.plus.web.controller;

import io.github.guanxiangkai.web.plus.core.model.PageQuery;
import io.github.guanxiangkai.web.plus.web.annotation.ApiCrypto;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseControllerApiCryptoAnnotationTest {

    @Test
    void shouldKeepBaseCrudEndpointsAsStandardJson() throws NoSuchMethodException {
        assertPlain("list", PageQuery.class);
        assertPlain("detail", String.class);
        assertPlain("create", Object.class);
        assertPlain("update", String.class, Object.class);
        assertPlain("updateEnabled", String.class, Boolean.class);
        assertPlain("batchUpdateEnabled", List.class, Boolean.class);
        assertPlain("delete", String.class);
        assertPlain("batchDelete", List.class);
    }

    @Test
    void shouldKeepUploadEndpointPlain() throws NoSuchMethodException {
        assertPlain("importData", Mono.class);
    }

    @Test
    void shouldNotExposeUnboundedListOrGenericExportEndpoints() {
        assertMethodAbsent("all");
        assertMethodAbsent("exportExcel");
        assertMethodAbsent("exportWord");
        assertMethodAbsent("exportPdf");
        assertDeclaredMethodAbsent("exportToExcel", List.class);
        assertDeclaredMethodAbsent("exportToWord", List.class);
        assertDeclaredMethodAbsent("exportToPdf", List.class);
    }

    private void assertPlain(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        assertThat(method(methodName, parameterTypes).getAnnotation(ApiCrypto.class)).isNull();
    }

    private Method method(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        return BaseController.class.getMethod(methodName, parameterTypes);
    }

    private void assertMethodAbsent(String methodName) {
        assertThatThrownBy(() -> BaseController.class.getMethod(methodName))
                .isInstanceOf(NoSuchMethodException.class);
    }

    private void assertDeclaredMethodAbsent(String methodName, Class<?>... parameterTypes) {
        assertThatThrownBy(() -> BaseController.class.getDeclaredMethod(methodName, parameterTypes))
                .isInstanceOf(NoSuchMethodException.class);
    }
}
