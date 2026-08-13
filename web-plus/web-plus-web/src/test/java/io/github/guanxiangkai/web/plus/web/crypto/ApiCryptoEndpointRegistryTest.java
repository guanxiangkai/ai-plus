package io.github.guanxiangkai.web.plus.web.crypto;

import io.github.guanxiangkai.web.plus.web.annotation.ApiCrypto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ApiCryptoEndpointRegistryTest {

    @Test
    void shouldScanMethodLevelApiCryptoRule() throws Exception {
        RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
        Method method = TestController.class.getMethod("secureSave");
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/api/secure/{id}").methods(RequestMethod.POST).build(),
                new TestController(),
                method
        );
        ApiCryptoEndpointRegistry registry = new ApiCryptoEndpointRegistry(provider(handlerMapping));

        List<ApiCryptoEndpointRule> rules = registry.rules();

        assertThat(rules).hasSize(1);
        assertThat(rules.getFirst().methods()).containsExactly("POST");
        assertThat(rules.getFirst().patterns()).containsExactly("/api/secure/{id}");
        assertThat(rules.getFirst().request()).isTrue();
        assertThat(rules.getFirst().response()).isFalse();
        assertThat(registry.find(MockServerHttpRequest.post("/api/secure/1").build())).contains(rules.getFirst());
        assertThat(registry.find(MockServerHttpRequest.get("/api/secure/1").build())).isEmpty();
    }

    @Test
    void shouldUseTypeLevelApiCryptoRule() throws Exception {
        RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
        Method method = TypeLevelController.class.getMethod("detail");
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/api/type/detail").methods(RequestMethod.GET).build(),
                new TypeLevelController(),
                method
        );
        ApiCryptoEndpointRegistry registry = new ApiCryptoEndpointRegistry(provider(handlerMapping));

        ApiCryptoEndpointRule rule = registry.rules().getFirst();

        assertThat(rule.request()).isTrue();
        assertThat(rule.response()).isTrue();
        assertThat(registry.find(MockServerHttpRequest.get("/api/type/detail").build())).contains(rule);
    }

    @Test
    void shouldIgnorePlainController() throws Exception {
        RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
        Method method = PlainController.class.getMethod("plain");
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/api/plain").methods(RequestMethod.GET).build(),
                new PlainController(),
                method
        );
        ApiCryptoEndpointRegistry registry = new ApiCryptoEndpointRegistry(provider(handlerMapping));

        assertThat(registry.rules()).isEmpty();
        assertThat(registry.find(MockServerHttpRequest.get("/api/plain").build())).isEmpty();
    }

    @Test
    void shouldIgnoreInternalApiCryptoRule() throws Exception {
        RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
        Method method = TestController.class.getMethod("secureSave");
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/internal/secure").methods(RequestMethod.POST).build(),
                new TestController(),
                method
        );
        ApiCryptoEndpointRegistry registry = new ApiCryptoEndpointRegistry(provider(handlerMapping));

        assertThat(registry.rules()).isEmpty();
        assertThat(registry.find(MockServerHttpRequest.post("/internal/secure").build())).isEmpty();
    }

    private ObjectProvider<RequestMappingHandlerMapping> provider(RequestMappingHandlerMapping handlerMapping) {
        return new ObjectProvider<>() {
            @Override
            public RequestMappingHandlerMapping getObject() {
                return handlerMapping;
            }

            @Override
            public Stream<RequestMappingHandlerMapping> stream() {
                return Stream.of(handlerMapping);
            }

            @Override
            public Stream<RequestMappingHandlerMapping> orderedStream() {
                return Stream.of(handlerMapping);
            }
        };
    }

    @RestController
    static class TestController {

        @ApiCrypto(response = false)
        public String secureSave() {
            return "ok";
        }
    }

    @ApiCrypto
    @RestController
    static class TypeLevelController {

        public String detail() {
            return "ok";
        }
    }

    @RestController
    static class PlainController {

        public String plain() {
            return "ok";
        }
    }
}
