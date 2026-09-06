package io.github.guanxiangkai.web.plus.security.client;

import io.github.guanxiangkai.web.plus.core.constants.AuthConstants;
import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TenantForwardingExchangeFilterFunctionTest {

    @Test
    void forwardsReactiveTenantOnlyToAllowedOrigin() {
        CountingProvider provider = new CountingProvider(Mono.just(Optional.of(user("tenant-a"))));
        TenantForwardingExchangeFilterFunction filter = filter(provider, "https://orders.example.test");
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        StepVerifier.create(filter.filter(request("https://orders.example.test/api/orders", null), request -> {
                    captured.set(request);
                    return success();
                }))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captured.get().headers().getFirst(AuthConstants.HeaderConstants.TENANT_ID)).isEqualTo("tenant-a");
        assertThat(provider.calls()).isEqualTo(1);
    }

    @Test
    void removesTenantHeaderForDeniedSchemeAndPortWithoutReadingProvider() {
        CountingProvider provider = new CountingProvider(Mono.just(Optional.of(user("tenant-a"))));
        TenantForwardingExchangeFilterFunction filter = filter(provider, "https://orders.example.test");
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        StepVerifier.create(filter.filter(request("http://orders.example.test/api/orders", "explicit"), request -> {
                    captured.set(request);
                    return success();
                }))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(filter.filter(request("https://orders.example.test:8443/api/orders", "explicit"), request -> {
                    captured.set(request);
                    return success();
                }))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captured.get().headers()).doesNotContainKey(AuthConstants.HeaderConstants.TENANT_ID);
        assertThat(provider.calls()).isZero();
    }

    @Test
    void preservesValidExplicitTenantWithoutReadingProvider() {
        CountingProvider provider = new CountingProvider(Mono.error(new IllegalStateException("must not be called")));
        TenantForwardingExchangeFilterFunction filter = filter(provider, "https://orders.example.test");
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        StepVerifier.create(filter.filter(request("https://orders.example.test/api/orders", "explicit"), request -> {
                    captured.set(request);
                    return success();
                }))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captured.get().headers().getFirst(AuthConstants.HeaderConstants.TENANT_ID)).isEqualTo("explicit");
        assertThat(provider.calls()).isZero();
    }

    @Test
    void removesTenantForRelativeAndUserInfoUrisWithoutReadingProvider() {
        CountingProvider provider = new CountingProvider(Mono.error(new IllegalStateException("must not be called")));
        TenantForwardingExchangeFilterFunction filter = filter(provider, "https://orders.example.test");
        for (String uri : List.of("/orders", "https://user@orders.example.test/orders")) {
            AtomicReference<ClientRequest> captured = new AtomicReference<>();
            StepVerifier.create(filter.filter(request(uri, "explicit"), forwarded -> {
                        captured.set(forwarded);
                        return success();
                    }))
                    .expectNextCount(1)
                    .verifyComplete();
            assertThat(captured.get().headers()).doesNotContainKey(AuthConstants.HeaderConstants.TENANT_ID);
        }
        assertThat(provider.calls()).isZero();
    }

    @Test
    void rejectsAmbiguousAndControlCharacterTenantHeadersBeforeSending() {
        CountingProvider provider = new CountingProvider(Mono.just(Optional.of(user("tenant-a\r\ninjected"))));
        TenantForwardingExchangeFilterFunction filter = filter(provider, "https://orders.example.test");
        AtomicInteger sends = new AtomicInteger();
        ClientRequest duplicate = ClientRequest.from(request("https://orders.example.test/orders", "tenant-a"))
                .header(AuthConstants.HeaderConstants.TENANT_ID, "tenant-b").build();
        for (ClientRequest outbound : List.of(duplicate, request("https://orders.example.test/orders", null))) {
            StepVerifier.create(filter.filter(outbound, forwarded -> {
                        sends.incrementAndGet();
                        return success();
                    }))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        assertThat(sends.get()).isZero();
    }

    @Test
    void removesBlankTenantHeaderWhenCurrentUserHasNoTenant() {
        CountingProvider provider = new CountingProvider(Mono.just(Optional.of(user(null))));
        TenantForwardingExchangeFilterFunction filter = filter(provider, "https://orders.example.test");
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        StepVerifier.create(filter.filter(request("https://orders.example.test/api/orders", " "), request -> {
                    captured.set(request);
                    return success();
                }))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captured.get().headers()).doesNotContainKey(AuthConstants.HeaderConstants.TENANT_ID);
    }

    @Test
    void readsProviderAtSubscriptionAndKeepsSubscriptionsIsolated() {
        AtomicInteger calls = new AtomicInteger();
        CurrentUserProvider provider = new CurrentUserProvider() {
            @Override
            public Optional<CurrentUser> getCurrentUser() {
                throw new AssertionError("WebClient 过滤器不能同步读取当前用户");
            }

            @Override
            public Mono<Optional<CurrentUser>> getCurrentUserMono() {
                return Mono.deferContextual(context -> {
                    calls.incrementAndGet();
                    return Mono.just(Optional.of(user(context.get("tenant"))));
                });
            }
        };
        TenantForwardingExchangeFilterFunction filter = filter(provider, "https://orders.example.test");
        List<String> tenants = new CopyOnWriteArrayList<>();
        Mono<ClientResponse> forwarded = filter.filter(request("https://orders.example.test/api/orders", null), request -> {
            tenants.add(request.headers().getFirst(AuthConstants.HeaderConstants.TENANT_ID));
            return success();
        });

        StepVerifier.create(forwarded.contextWrite(context -> context.put("tenant", "tenant-a")))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(forwarded.contextWrite(context -> context.put("tenant", "tenant-b")))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(tenants).containsExactly("tenant-a", "tenant-b");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void propagatesProviderFailureAndRejectsNonOriginConfiguration() {
        CountingProvider provider = new CountingProvider(Mono.error(new IllegalStateException("provider failed")));
        TenantForwardingExchangeFilterFunction filter = filter(provider, "https://orders.example.test");

        StepVerifier.create(filter.filter(request("https://orders.example.test/api/orders", null), request -> success()))
                .expectErrorMessage("provider failed")
                .verify();
        assertThatIllegalArgumentException().isThrownBy(() -> filter(provider, "https://user@orders.example.test"));
        assertThatIllegalArgumentException().isThrownBy(() -> filter(provider, "https://orders.example.test/api"));
        assertThatIllegalArgumentException().isThrownBy(() -> filter(provider, "https://orders.example.test?debug=true"));
    }

    private static TenantForwardingExchangeFilterFunction filter(CurrentUserProvider provider, String origin) {
        return new TenantForwardingExchangeFilterFunction(provider, List.of(URI.create(origin)));
    }

    private static ClientRequest request(String uri, String tenantId) {
        ClientRequest.Builder builder = ClientRequest.create(HttpMethod.GET, URI.create(uri));
        if (tenantId != null) {
            builder.header(AuthConstants.HeaderConstants.TENANT_ID, tenantId);
        }
        return builder.build();
    }

    private static Mono<ClientResponse> success() {
        return Mono.just(ClientResponse.create(HttpStatus.OK).build());
    }

    private static CurrentUser user(String tenantId) {
        return new CurrentUser("user-1", null, tenantId, null,
                Set.of(), Set.of(), Set.of(), false, null, 0L, Map.of());
    }

    private static final class CountingProvider implements CurrentUserProvider {
        private final Mono<Optional<CurrentUser>> currentUser;
        private final AtomicInteger invocations = new AtomicInteger();

        private CountingProvider(Mono<Optional<CurrentUser>> currentUser) {
            this.currentUser = currentUser;
        }

        @Override
        public Optional<CurrentUser> getCurrentUser() {
            return Optional.empty();
        }

        @Override
        public Mono<Optional<CurrentUser>> getCurrentUserMono() {
            return Mono.defer(() -> {
                invocations.incrementAndGet();
                return currentUser;
            });
        }

        int calls() {
            return invocations.get();
        }
    }
}
