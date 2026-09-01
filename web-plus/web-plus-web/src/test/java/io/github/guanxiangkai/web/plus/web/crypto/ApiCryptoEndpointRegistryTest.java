package io.github.guanxiangkai.web.plus.web.crypto;

import io.github.guanxiangkai.web.plus.web.annotation.ApiCrypto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
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
        assertThat(registry.find(exchange(MockServerHttpRequest.post("/api/secure/1").build()))
                .flatMap(ApiCryptoEndpointRegistry.EndpointMatch::rule))
                .contains(rules.getFirst());
        assertThat(registry.find(exchange(MockServerHttpRequest.get("/api/secure/1").build()))).isEmpty();
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
        assertThat(registry.find(exchange(MockServerHttpRequest.get("/api/type/detail").build()))
                .flatMap(ApiCryptoEndpointRegistry.EndpointMatch::rule)).contains(rule);
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
        assertThat(registry.find(exchange(MockServerHttpRequest.get("/api/plain").build()))
                .orElseThrow()
                .rule()).isEmpty();
    }

    @Test
    void shouldScanExplicitInternalApiCryptoRule() throws Exception {
        RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
        Method method = TestController.class.getMethod("secureSave");
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/internal/secure").methods(RequestMethod.POST).build(),
                new TestController(),
                method
        );
        ApiCryptoEndpointRegistry registry = new ApiCryptoEndpointRegistry(provider(handlerMapping));

        assertThat(registry.rules()).singleElement().satisfies(rule -> {
            assertThat(rule.patterns()).containsExactly("/internal/secure");
            assertThat(rule.request()).isTrue();
            assertThat(rule.response()).isFalse();
        });
        assertThat(registry.find(exchange(MockServerHttpRequest.post("/internal/secure").build()))).isPresent();
    }

    @Test
    void shouldDistinguishEncryptedAndPlainEndpointsByConsumesCondition() throws Exception {
        RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
        MixedController controller = new MixedController();
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/api/invoke")
                        .methods(RequestMethod.POST)
                        .consumes(MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                controller,
                MixedController.class.getMethod("secureJson")
        );
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/api/invoke")
                        .methods(RequestMethod.POST)
                        .consumes(MediaType.TEXT_PLAIN_VALUE)
                        .build(),
                controller,
                MixedController.class.getMethod("plainText")
        );
        ApiCryptoEndpointRegistry registry = new ApiCryptoEndpointRegistry(provider(handlerMapping));

        assertThat(registry.find(exchange(MockServerHttpRequest.post("/api/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .build()))).isPresent();
        assertThat(registry.find(exchange(MockServerHttpRequest.post("/api/invoke")
                        .contentType(MediaType.TEXT_PLAIN)
                        .build()))
                .orElseThrow()
                .rule()).isEmpty();
        assertThat(registry.rules()).singleElement().satisfies(rule ->
                assertThat(rule.patterns()).containsExactly("/api/invoke"));
    }

    @Test
    void shouldPreferMoreSpecificPlainEndpointByParamsAndHeaders() throws Exception {
        RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
        MixedController controller = new MixedController();
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/api/route")
                        .methods(RequestMethod.POST)
                        .build(),
                controller,
                MixedController.class.getMethod("secureDefault")
        );
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/api/route")
                        .methods(RequestMethod.POST)
                        .params("source=dify")
                        .headers("X-Caller=dify")
                        .build(),
                controller,
                MixedController.class.getMethod("plainDify")
        );
        ApiCryptoEndpointRegistry registry = new ApiCryptoEndpointRegistry(provider(handlerMapping));

        assertThat(registry.find(exchange(MockServerHttpRequest.post("/api/route").build()))).isPresent();
        assertThat(registry.find(exchange(MockServerHttpRequest.post("/api/route")
                        .queryParam("source", "dify")
                        .header("X-Caller", "dify")
                        .build()))
                .orElseThrow()
                .rule()).isEmpty();
    }

    @Test
    void shouldResolveEncryptedQueryAfterParamsBecomeAvailable() throws Exception {
        RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
        MixedController controller = new MixedController();
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/api/search")
                        .methods(RequestMethod.GET)
                        .params("tenant")
                        .build(),
                controller,
                MixedController.class.getMethod("secureTenant")
        );
        ApiCryptoEndpointRegistry registry = new ApiCryptoEndpointRegistry(provider(handlerMapping));
        MockServerWebExchange encryptedExchange = exchange(MockServerHttpRequest.get("/api/search")
                .queryParam(ApiCryptoService.CRYPTO_QUERY_PARAM, "encrypted-envelope")
                .build());

        assertThat(registry.find(encryptedExchange)).isEmpty();
        ApiCryptoEndpointRegistry.EncryptedQueryCandidates candidates = registry
                .findEncryptedQueryCandidates(encryptedExchange)
                .orElseThrow();
        ApiCryptoEndpointRegistry.EndpointMatch decryptedMatch = registry.find(exchange(
                        MockServerHttpRequest.get("/api/search").queryParam("tenant", "tenant-a").build()))
                .orElseThrow();

        assertThat(candidates.accepts(decryptedMatch)).isTrue();
    }

    @Test
    void shouldKeepAllEncryptedCandidatesUntilFullParamsMatch() throws Exception {
        RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
        MixedController controller = new MixedController();
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/api/search")
                        .methods(RequestMethod.GET)
                        .params("tenant")
                        .build(),
                controller,
                MixedController.class.getMethod("secureTenant")
        );
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/api/search")
                        .methods(RequestMethod.GET)
                        .headers("X-Client=special")
                        .params("region")
                        .build(),
                controller,
                MixedController.class.getMethod("secureRegion")
        );
        ApiCryptoEndpointRegistry registry = new ApiCryptoEndpointRegistry(provider(handlerMapping));
        ApiCryptoEndpointRegistry.EncryptedQueryCandidates candidates = registry
                .findEncryptedQueryCandidates(exchange(MockServerHttpRequest.get("/api/search")
                        .header("X-Client", "special")
                        .queryParam(ApiCryptoService.CRYPTO_QUERY_PARAM, "encrypted-envelope")
                        .build()))
                .orElseThrow();
        ApiCryptoEndpointRegistry.EndpointMatch decryptedMatch = registry.find(exchange(
                        MockServerHttpRequest.get("/api/search")
                                .header("X-Client", "special")
                                .queryParam("tenant", "tenant-a")
                                .build()))
                .orElseThrow();

        assertThat(decryptedMatch.rule()).isPresent();
        assertThat(candidates.accepts(decryptedMatch)).isTrue();
    }

    @Test
    void shouldRejectEncryptedQueryThatRoutesToPlainEndpointAfterDecryption() throws Exception {
        RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
        MixedController controller = new MixedController();
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/api/search")
                        .methods(RequestMethod.GET)
                        .params("tenant")
                        .build(),
                controller,
                MixedController.class.getMethod("secureTenant")
        );
        handlerMapping.registerMapping(
                RequestMappingInfo.paths("/api/search")
                        .methods(RequestMethod.GET)
                        .params("source=dify")
                        .build(),
                controller,
                MixedController.class.getMethod("plainDify")
        );
        ApiCryptoEndpointRegistry registry = new ApiCryptoEndpointRegistry(provider(handlerMapping));
        ApiCryptoEndpointRegistry.EncryptedQueryCandidates candidates = registry
                .findEncryptedQueryCandidates(exchange(MockServerHttpRequest.get("/api/search")
                        .queryParam(ApiCryptoService.CRYPTO_QUERY_PARAM, "encrypted-envelope")
                        .build()))
                .orElseThrow();
        ApiCryptoEndpointRegistry.EndpointMatch decryptedMatch = registry.find(exchange(
                        MockServerHttpRequest.get("/api/search").queryParam("source", "dify").build()))
                .orElseThrow();

        assertThat(decryptedMatch.rule()).isEmpty();
        assertThat(candidates.accepts(decryptedMatch)).isFalse();
    }

    private MockServerWebExchange exchange(MockServerHttpRequest request) {
        return MockServerWebExchange.from(request);
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

    @RestController
    static class MixedController {

        @ApiCrypto
        public String secureJson() {
            return "ok";
        }

        public String plainText() {
            return "ok";
        }

        @ApiCrypto
        public String secureDefault() {
            return "ok";
        }

        @ApiCrypto
        public String secureTenant() {
            return "ok";
        }

        @ApiCrypto
        public String secureRegion() {
            return "ok";
        }

        public String plainDify() {
            return "ok";
        }
    }
}
