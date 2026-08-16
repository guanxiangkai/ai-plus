package io.github.guanxiangkai.web.plus.core.net;

import io.github.guanxiangkai.web.plus.core.constants.AuthConstants;
import io.github.guanxiangkai.web.plus.core.properties.ClientIpProperties;
import io.github.guanxiangkai.web.plus.core.properties.TrustedForwardProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedProxyClientIpResolverTest {

    @Test
    void acceptsGatewayVerifiedIpOnlyWithMatchingForwardToken() {
        TrustedForwardProperties trustedForward = trustedForward("trusted-token");
        TrustedProxyClientIpResolver resolver = new TrustedProxyClientIpResolver(
                new ClientIpProperties(List.of()), trustedForward);

        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header(trustedForward.getHeaderName(), "trusted-token")
                .header(AuthConstants.HeaderConstants.VERIFIED_CLIENT_IP, "203.0.113.9")
                .remoteAddress(new InetSocketAddress("198.51.100.5", 8080))
                .build();

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void rejectsSpoofedVerifiedIpWithoutMatchingForwardToken() {
        TrustedForwardProperties trustedForward = trustedForward("trusted-token");
        TrustedProxyClientIpResolver resolver = new TrustedProxyClientIpResolver(
                new ClientIpProperties(List.of()), trustedForward);

        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header(trustedForward.getHeaderName(), "spoofed-token")
                .header(AuthConstants.HeaderConstants.VERIFIED_CLIENT_IP, "203.0.113.9")
                .remoteAddress(new InetSocketAddress("198.51.100.5", 8080))
                .build();

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.5");
    }

    private static TrustedForwardProperties trustedForward(String token) {
        TrustedForwardProperties properties = new TrustedForwardProperties();
        properties.setToken(token);
        return properties;
    }
}
