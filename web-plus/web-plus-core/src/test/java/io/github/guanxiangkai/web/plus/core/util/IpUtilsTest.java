package io.github.guanxiangkai.web.plus.core.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class IpUtilsTest {

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
        headers.add("X-Forwarded-For", "203.0.113.10, 10.0.0.8");

        String clientIp = IpUtils.getClientIp(headers, new InetSocketAddress("10.0.0.2", 8080));

        assertThat(clientIp).isEqualTo("203.0.113.10");
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
