package io.github.guanxiangkai.web.plus.core.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IpUtilsTest {

    @Test
    void missingRemoteAddressShouldReturnUnknownWithoutTrustingForwardedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "203.0.113.9");

        assertThat(IpUtils.getClientIp(headers, null)).isEqualTo("unknown");
    }

    @Test
    void nullLiteralShouldNotResolveAnAddress() {
        assertThat(IpUtils.normalizeIpLiteral(null)).isNull();
    }

    @Test
    void getClientIp_ignoresSpoofedForwardedHeadersFromPublicPeer() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "203.0.113.10");

        String clientIp = IpUtils.getClientIp(headers, new InetSocketAddress("198.51.100.5", 8080));

        assertThat(clientIp).isEqualTo("198.51.100.5");
    }

    @Test
    void getClientIp_acceptsForwardedHeadersFromTrustedInternalPeer() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "203.0.113.10, 127.0.0.8");

        String clientIp = IpUtils.getClientIp(headers, new InetSocketAddress("127.0.0.2", 8080));

        assertThat(clientIp).isEqualTo("203.0.113.10");
    }

    @Test
    void explicitResolverIgnoresForwardedHeadersFromUntrustedPeer() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "203.0.113.10");

        String clientIp = IpUtils.getClientIp(headers,
                new InetSocketAddress("198.51.100.5", 8080), List.of("198.51.100.6"));

        assertThat(clientIp).isEqualTo("198.51.100.5");
    }

    @Test
    void explicitResolverWalksForwardedChainFromTrustedEdge() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "192.0.2.44, 198.51.100.6");

        String clientIp = IpUtils.getClientIp(headers,
                new InetSocketAddress("198.51.100.7", 8080),
                List.of("198.51.100.6", "198.51.100.7"));

        assertThat(clientIp).isEqualTo("192.0.2.44");
    }

    @Test
    void explicitResolverRejectsAttackerPrefixBeforeUntrustedBoundary() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "192.0.2.99, 203.0.113.30, 198.51.100.6");

        String clientIp = IpUtils.getClientIp(headers,
                new InetSocketAddress("198.51.100.7", 8080),
                List.of("198.51.100.6", "198.51.100.7"));

        assertThat(clientIp).isEqualTo("203.0.113.30");
    }

    @Test
    void isIntranet_recognizesOnlyPrivate172Range() {
        assertThat(IpUtils.isIntranet("172.16.1.1")).isTrue();
        assertThat(IpUtils.isIntranet("172.31.255.255")).isTrue();
        assertThat(IpUtils.isIntranet("172.15.255.255")).isFalse();
        assertThat(IpUtils.isIntranet("172.32.0.1")).isFalse();
    }

    @Test
    void isIntranet_supportsIpv6UniqueLocalAddresses() {
        assertThat(IpUtils.isIntranet("fc00::1")).isTrue();
        assertThat(IpUtils.isIntranet("fe80::1")).isTrue();
        assertThat(IpUtils.isIntranet("2001:db8::1")).isFalse();
    }
}
