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
    void shouldEncryptJsonCrudEndpoints() throws NoSuchMethodException {
        assertEncrypted("list", PageQuery.class);
        assertEncrypted("detail", String.class);
        assertEncrypted("create", Object.class);
        assertEncrypted("update", String.class, Object.class);
        assertEncrypted("updateEnabled", String.class, Boolean.class);
        assertEncrypted("batchUpdateEnabled", List.class, Boolean.class);
        assertEncrypted("delete", String.class);
        assertEncrypted("batchDelete", List.class);
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

    private void assertEncrypted(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        ApiCrypto annotation = method(methodName, parameterTypes).getAnnotation(ApiCrypto.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.request()).isTrue();
        assertThat(annotation.response()).isTrue();
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
