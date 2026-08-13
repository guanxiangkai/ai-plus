package io.github.guanxiangkai.web.plus.security.handler;

import io.github.guanxiangkai.web.plus.core.constants.AuthConstants;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class CustomAuthenticationEntryPointTest {

    private final CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint();

    @Test
    void shouldTreatAuthorizationHeaderAsAuthenticationMaterial() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/sse/ticket")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .build()
        );

        assertThat(CustomAuthenticationEntryPoint.hasAuthenticationMaterial(exchange)).isTrue();
    }

    @Test
    void shouldTreatTrustedHeadersAsAuthenticationMaterial() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/sse/ticket")
                        .header(AuthConstants.HeaderConstants.USER_ID, "user-123")
                        .header(AuthConstants.HeaderConstants.TRUSTED_FORWARD_TOKEN, "trusted-token")
                        .build()
        );

        assertThat(CustomAuthenticationEntryPoint.hasAuthenticationMaterial(exchange)).isTrue();
    }

    @Test
    void shouldKeepUnauthorizedResponseWhenAuthenticationMaterialIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/sse/ticket").build()
        );

        entryPoint.commence(exchange, new BadCredentialsException("Not Authenticated")).block();

        assertThat(CustomAuthenticationEntryPoint.hasAuthenticationMaterial(exchange)).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }
}
