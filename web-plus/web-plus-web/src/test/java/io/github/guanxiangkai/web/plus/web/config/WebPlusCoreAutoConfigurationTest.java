package io.github.guanxiangkai.web.plus.web.config;

import io.github.guanxiangkai.jpa.plus.interceptor.tenant.spi.TenantIdProvider;
import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoException;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoPublicConfig;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoRuntimePolicy;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoService;
import io.github.guanxiangkai.web.plus.web.filter.ApiCryptoWebFilter;
import io.github.guanxiangkai.web.plus.web.properties.ApiCryptoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import reactor.core.scheduler.Scheduler;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WebPlusCoreAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebPlusCoreAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void shouldKeepApiCryptoDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ApiCryptoProperties.class);
            assertThat(context).hasBean("apiCryptoPublicConfigRouterFunction");
            assertThat(context).doesNotHaveBean(ApiCryptoService.class);
            assertThat(context).doesNotHaveBean(ApiCryptoWebFilter.class);
            assertThat(context).doesNotHaveBean(ApiCryptoRuntimePolicy.class);
            assertThat(context).doesNotHaveBean(WebPlusCoreAutoConfiguration.API_CRYPTO_SCHEDULER_BEAN_NAME);

            ApiCryptoProperties properties = context.getBean(ApiCryptoProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getStrategy()).isEqualTo(ApiCryptoProperties.Strategy.SM4_CBC_SM3_V1);
            assertThat(properties.getPbkdf2Iterations()).isEqualTo(120_000);
            assertThat(properties.getRequest().isEnabled()).isTrue();
            assertThat(properties.getRequest().getKeyId()).isEqualTo("request-default");
            assertThat(properties.getResponse().isEnabled()).isTrue();
            assertThat(properties.getResponse().getKeyId()).isEqualTo("response-default");
            assertThat(properties.getRuntime().toPolicy()).isEqualTo(ApiCryptoRuntimePolicy.defaults());
            assertThat(ApiCryptoPublicConfig.from(properties, List.of()).configPath()).isEqualTo("/web-plus/api-crypto/config");
        });
    }

    @Test
    void shouldExposePublicApiCryptoConfigRoute() {
        contextRunner.run(context -> {
            @SuppressWarnings("unchecked")
            RouterFunction<?> routerFunction = context.getBean(
                    "apiCryptoPublicConfigRouterFunction", RouterFunction.class);

            WebTestClient.bindToRouterFunction(routerFunction)
                    .build()
                    .get()
                    .uri(ApiCryptoPublicConfig.CONFIG_PATH)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.enabled").isEqualTo(false)
                    .jsonPath("$.strategy").isEqualTo("SM4_CBC_SM3_V1")
                    .jsonPath("$.request.keyId").isEqualTo("request-default")
                    .jsonPath("$.response.keyId").isEqualTo("response-default")
                    .jsonPath("$.headers.crypto").isEqualTo("X-Api-Crypto")
                    .jsonPath("$.queryParam").isEqualTo("__api_crypto")
                    .jsonPath("$.rules").isArray()
                    .jsonPath("$.configPath").isEqualTo(ApiCryptoPublicConfig.CONFIG_PATH);
        });
    }

    @Test
    void shouldCreateApiCryptoBeansWhenEnabledWithKeys() {
        contextRunner
                .withPropertyValues(
                        "web-plus.api-crypto.enabled=true",
                        "web-plus.api-crypto.request.key=reference-request-secret",
                        "web-plus.api-crypto.response.key=reference-response-secret"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ApiCryptoService.class);
                    assertThat(context).hasSingleBean(ApiCryptoWebFilter.class);
                    assertThat(context).hasSingleBean(ApiCryptoRuntimePolicy.class);
                    assertThat(context).hasBean(WebPlusCoreAutoConfiguration.API_CRYPTO_SCHEDULER_BEAN_NAME);
                    assertThat(context.getBean(
                            WebPlusCoreAutoConfiguration.API_CRYPTO_SCHEDULER_BEAN_NAME,
                            Scheduler.class)).isNotNull();
                    assertThat(context.getBean(ApiCryptoService.class).requestEnabled()).isTrue();
                    assertThat(context.getBean(ApiCryptoService.class).responseEnabled()).isTrue();
                });
    }

    @Test
    void shouldRejectInvalidApiCryptoRuntimeLimit() {
        contextRunner
                .withPropertyValues(
                        "web-plus.api-crypto.enabled=true",
                        "web-plus.api-crypto.request.key=reference-request-secret",
                        "web-plus.api-crypto.response.key=reference-response-secret",
                        "web-plus.api-crypto.runtime.max-request-body-size=0B"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("maxRequestBodySize 必须大于 0");
                });
    }

    @Test
    void shouldFailFastWhenEnabledWithoutRequiredKeys() {
        contextRunner
                .withPropertyValues("web-plus.api-crypto.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasRootCauseInstanceOf(ApiCryptoException.class)
                            .hasStackTraceContaining("接口加密密钥未配置");
                });
    }

    @Test
    void shouldExposeJpaPlusTenantIdProviderWithConcreteType() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TenantIdProvider.class);

            CurrentUserHolder.set(new CurrentUser(
                    "user-123",
                    "alice",
                    "tenant-456",
                    "dept-1",
                    Set.of("dept-1"),
                    Set.of("USER"),
                    Set.of("agent:query"),
                    false,
                    "WEB",
                    System.currentTimeMillis(),
                    Map.of()
            ));
            try {
                TenantIdProvider provider = context.getBean(TenantIdProvider.class);
                assertThat(provider.getCurrentTenantId()).isEqualTo("tenant-456");
            } finally {
                CurrentUserHolder.clear();
            }
        });
    }

    @Test
    void shouldBackOffWhenCustomJpaPlusTenantIdProviderExists() {
        contextRunner
                .withBean(TenantIdProvider.class, () -> () -> "custom-tenant")
                .run(context -> {
                    assertThat(context).hasSingleBean(TenantIdProvider.class);
                    assertThat(context.getBean(TenantIdProvider.class).getCurrentTenantId())
                            .isEqualTo("custom-tenant");
                });
    }
}
