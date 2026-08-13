package io.github.guanxiangkai.web.plus.web.controller;

import io.github.guanxiangkai.web.plus.core.model.ApiResponse;
import io.github.guanxiangkai.web.plus.core.model.PageQuery;
import io.github.guanxiangkai.web.plus.core.model.PageResponse;
import io.github.guanxiangkai.web.plus.log.annotation.OperationLog;
import io.github.guanxiangkai.web.plus.security.annotation.RequiresPermission;
import io.github.guanxiangkai.web.plus.web.annotation.ApiCrypto;
import io.github.guanxiangkai.web.plus.web.service.IBaseService;
import io.github.guanxiangkai.web.plus.web.service.IReadOnlyService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadOnlyBaseControllerTest {

    @Test
    void shouldUseReadOnlyServiceForListAndDetailEndpoints() {
        TestReadOnlyService service = new TestReadOnlyService();
        TestController controller = new TestController(service);
        TestQuery query = new TestQuery(2, 20, "name", "ASC");

        ApiResponse<PageResponse<String>> listResponse = controller.list(query).block();
        ApiResponse<DetailView> detailResponse = controller.detail("record-1").block();

        assertThat(listResponse).isNotNull();
        assertThat(listResponse.data().records()).containsExactly("page-item");
        assertThat(service.lastQuery).isSameAs(query);
        assertThat(detailResponse).isNotNull();
        assertThat(detailResponse.data()).isEqualTo(new DetailView("record-1"));
    }

    @Test
    void shouldExecuteBlockingReadOnlyServiceCallsOnBoundedElasticScheduler() {
        TestReadOnlyService service = new TestReadOnlyService();
        TestController controller = new TestController(service);

        controller.list(new TestQuery(1, 10, null, null)).block();
        controller.detail("record-1").block();

        assertThat(service.executionThreads)
                .hasSize(2)
                .allSatisfy(threadName -> assertThat(threadName).startsWith("boundedElastic-"));
    }

    @Test
    void shouldDeclareTheSameSecurityLogAndCryptoContractAsCrudQueries() throws NoSuchMethodException {
        assertQueryContract("list", PageQuery.class, "#{getPermissionPrefix() + ':list'}");
        assertQueryContract("detail", String.class, "#{getPermissionPrefix() + ':query'}");
    }

    @Test
    void shouldExposeCrudServiceAsReadOnlyService() {
        assertThat(IReadOnlyService.class.isAssignableFrom(IBaseService.class)).isTrue();
    }

    @Test
    void shouldExposeSpelMetadataFromTheSingleReadOnlyBaseClass() {
        TestController controller = new TestController(new TestReadOnlyService());

        assertThat(controller.getPermissionPrefix()).isEqualTo("web:test");
        assertThat(controller.getModuleName()).isEqualTo("Web");
        assertThat(controller.getEntityName()).isEqualTo("Test");
    }

    @Test
    void shouldNotExposeUnboundedAllEndpoint() {
        assertThatThrownBy(() -> ReadOnlyBaseController.class.getMethod("all"))
                .isInstanceOf(NoSuchMethodException.class);
    }

    private void assertQueryContract(String methodName, String permission) throws NoSuchMethodException {
        assertQueryContract(methodName, new Class<?>[0], permission);
    }

    private void assertQueryContract(String methodName, Class<?> parameterType, String permission)
            throws NoSuchMethodException {
        assertQueryContract(methodName, new Class<?>[]{parameterType}, permission);
    }

    private void assertQueryContract(String methodName, Class<?>[] parameterTypes, String permission)
            throws NoSuchMethodException {
        Method method = ReadOnlyBaseController.class.getMethod(methodName, parameterTypes);

        assertThat(method.getAnnotation(RequiresPermission.class).value()).containsExactly(permission);
        assertThat(method.getAnnotation(OperationLog.class)).isNotNull();
        ApiCrypto crypto = method.getAnnotation(ApiCrypto.class);
        assertThat(crypto).isNotNull();
        assertThat(crypto.request()).isTrue();
        assertThat(crypto.response()).isTrue();
    }

    private static final class TestController extends ReadOnlyBaseController<TestQuery, String, DetailView> {

        private final IReadOnlyService<TestQuery, String, DetailView> service;

        private TestController(IReadOnlyService<TestQuery, String, DetailView> service) {
            this.service = service;
        }

        @Override
        protected IReadOnlyService<TestQuery, String, DetailView> getService() {
            return service;
        }
    }

    private static final class TestReadOnlyService implements IReadOnlyService<TestQuery, String, DetailView> {

        private TestQuery lastQuery;
        private final List<String> executionThreads = new CopyOnWriteArrayList<>();

        @Override
        public PageResponse<String> list(TestQuery query) {
            lastQuery = query;
            executionThreads.add(Thread.currentThread().getName());
            return PageResponse.of(List.of("page-item"), 1, query.page(), query.size());
        }

        @Override
        public DetailView detail(String id) {
            executionThreads.add(Thread.currentThread().getName());
            return new DetailView(id);
        }
    }

    private record TestQuery(int page, int size, String sortBy, String sortDir) implements PageQuery {
    }

    private record DetailView(String id) {
    }
}
