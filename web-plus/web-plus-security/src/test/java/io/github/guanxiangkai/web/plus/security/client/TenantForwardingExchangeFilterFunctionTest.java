package io.github.guanxiangkai.web.plus.security.client;

import io.github.guanxiangkai.web.plus.core.constants.AuthConstants;
import io.github.guanxiangkai.web.plus.security.context.UserContext;
import io.github.guanxiangkai.web.plus.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantForwardingExchangeFilterFunctionTest {

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void shouldPreserveExplicitTenantHeader() {
        UserContextHolder.set(userContext("context-tenant"));
        TenantForwardingExchangeFilterFunction filter =
                new TenantForwardingExchangeFilterFunction(() -> "background-tenant");
        ClientRequest request = requestBuilder()
                .header(AuthConstants.HeaderConstants.TENANT_ID, "explicit-tenant")
                .build();

        ClientRequest forwarded = exchange(filter, request);

        assertThat(forwarded.headers().getFirst(AuthConstants.HeaderConstants.TENANT_ID))
                .isEqualTo("explicit-tenant");
    }

    @Test
    void shouldPreferSecurityContextAndNormalizeTenantId() {
        UserContextHolder.set(userContext("  context-tenant  "));
        TenantForwardingExchangeFilterFunction filter =
                new TenantForwardingExchangeFilterFunction(() -> "background-tenant");

        ClientRequest forwarded = exchange(filter, requestBuilder().build());

        assertThat(forwarded.headers().getFirst(AuthConstants.HeaderConstants.TENANT_ID))
                .isEqualTo("context-tenant");
    }

    @Test
    void shouldUseFirstNonBlankFallbackTenant() {
        TenantForwardingExchangeFilterFunction filter =
                new TenantForwardingExchangeFilterFunction(() -> " ", () -> " tenant-job ");

        ClientRequest forwarded = exchange(filter, requestBuilder().build());

        assertThat(forwarded.headers().getFirst(AuthConstants.HeaderConstants.TENANT_ID))
                .isEqualTo("tenant-job");
    }

    @Test
    void shouldLeaveRequestUnchangedWhenTenantIsUnavailable() {
        TenantForwardingExchangeFilterFunction filter =
                new TenantForwardingExchangeFilterFunction(() -> null);
        ClientRequest request = requestBuilder().build();

        ClientRequest forwarded = exchange(filter, request);

        assertThat(forwarded).isSameAs(request);
        assertThat(forwarded.headers().getFirst(AuthConstants.HeaderConstants.TENANT_ID)).isNull();
    }

    private static ClientRequest.Builder requestBuilder() {
        return ClientRequest.create(HttpMethod.GET, URI.create("http://platform-system/internal/ping"));
    }

    private static ClientRequest exchange(
            TenantForwardingExchangeFilterFunction filter,
            ClientRequest request) {
        AtomicReference<ClientRequest> forwarded = new AtomicReference<>();
        filter.filter(request, actual -> {
            forwarded.set(actual);
            return reactor.core.publisher.Mono.just(ClientResponse.create(HttpStatus.OK).build());
        }).block();
        return forwarded.get();
    }

    private static UserContext userContext(String tenantId) {
        return new UserContext(
                "user-1",
                tenantId,
                false,
                null,
                Set.of(),
                Set.of(),
                Set.of(),
                Map.of());
    }
}
