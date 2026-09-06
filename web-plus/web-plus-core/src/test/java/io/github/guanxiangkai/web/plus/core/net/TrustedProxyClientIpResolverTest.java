package io.github.guanxiangkai.web.plus.core.net;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedProxyClientIpResolverTest {

    @Test
    void resolvesIpv4ClientAfterRemovingTrustedProxyHops() {
        ClientIpResolver resolver = new TrustedProxyClientIpResolver(List.of("10.0.0.0/8"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "203.0.113.10, 10.1.2.3")
                .remoteAddress(address("10.0.0.2"))
                .build();

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void resolvesIpv6ClientAndTrustedIpv6Cidr() {
        ClientIpResolver resolver = new TrustedProxyClientIpResolver(List.of("2001:db8:abcd::/48"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "2001:db8:ffff::4, 2001:db8:abcd::3")
                .remoteAddress(address("2001:db8:abcd::2"))
                .build();

        assertThat(resolver.resolve(request))
                .isEqualTo(InetAddress.ofLiteral("2001:db8:ffff::4").getHostAddress());
    }

    @Test
    void ignoresForwardedChainWhenTcpPeerIsNotTrusted() {
        ClientIpResolver resolver = new TrustedProxyClientIpResolver(List.of("10.0.0.0/8"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "203.0.113.10, 10.1.2.3")
                .remoteAddress(address("198.51.100.2"))
                .build();

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.2");
    }

    @Test
    void rejectsUnknownHostnamesEmptyTokensAndAcceptsMultipleHeaderLinesOnlyWhenWholeChainIsValid() {
        ClientIpResolver resolver = new TrustedProxyClientIpResolver(List.of("10.0.0.0/8"));

        MockServerHttpRequest valid = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "203.0.113.10")
                .header("X-Forwarded-For", "10.1.2.3")
                .remoteAddress(address("10.0.0.2"))
                .build();
        MockServerHttpRequest unknown = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "203.0.113.10, unknown")
                .remoteAddress(address("10.0.0.2"))
                .build();
        MockServerHttpRequest hostname = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "client.example.test, 10.1.2.3")
                .remoteAddress(address("10.0.0.2"))
                .build();
        MockServerHttpRequest emptyToken = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "203.0.113.10,")
                .remoteAddress(address("10.0.0.2"))
                .build();

        assertThat(resolver.resolve(valid)).isEqualTo("203.0.113.10");
        assertThat(resolver.resolve(unknown)).isEqualTo("10.0.0.2");
        assertThat(resolver.resolve(hostname)).isEqualTo("10.0.0.2");
        assertThat(resolver.resolve(emptyToken)).isEqualTo("10.0.0.2");
    }

    @Test
    void returnsUnknownWhenTcpPeerIsAbsent() {
        ClientIpResolver resolver = new TrustedProxyClientIpResolver(List.of("10.0.0.0/8"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "203.0.113.10")
                .build();

        assertThat(resolver.resolve(request)).isEqualTo("unknown");
    }

    private static InetSocketAddress address(String ip) {
        return new InetSocketAddress(InetAddress.ofLiteral(ip), 8080);
    }
}
