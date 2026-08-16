package io.github.guanxiangkai.web.plus.security.properties;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthPropertiesTest {

    @Test
    void gatewayPassthrough_requiresAtLeastOneTrustedIp() {
        assertThatThrownBy(() -> new AuthProperties(
                true,
                "JWT",
                "Authorization",
                "Bearer ",
                "0123456789abcdef0123456789abcdef",
                7_200_000L,
                false,
                List.of("/public/**"),
                true,
                Arrays.asList(" ", null),
                List.of()
        )).hasMessageContaining("gateway-trusted-ips");
    }

    @Test
    void trimsTrustedAddressLists() {
        AuthProperties properties = new AuthProperties(
                true,
                "JWT",
                "Authorization",
                "Bearer ",
                "0123456789abcdef0123456789abcdef",
                7_200_000L,
                false,
                List.of("/public/**"),
                true,
                List.of(" 203.0.113.1 ", "", "203.0.113.2"),
                List.of(" 203.0.113.9 ", " ")
        );

        assertThat(properties.gatewayTrustedIps()).containsExactly("203.0.113.1", "203.0.113.2");
        assertThat(properties.gatewayTrustedProxyIps()).containsExactly("203.0.113.9");
    }

    @Test
    void normalizesIpv6TrustedAddresses() {
        AuthProperties properties = new AuthProperties(
                true,
                "JWT",
                "Authorization",
                "Bearer ",
                "0123456789abcdef0123456789abcdef",
                7_200_000L,
                false,
                List.of("/public/**"),
                true,
                List.of("::1"),
                List.of()
        );

        assertThat(properties.gatewayTrustedIps()).containsExactly("0:0:0:0:0:0:0:1");
    }

    @Test
    void rejectsNonLiteralTrustedAddresses() {
        assertThatThrownBy(() -> new AuthProperties(
                true,
                "JWT",
                "Authorization",
                "Bearer ",
                "0123456789abcdef0123456789abcdef",
                7_200_000L,
                false,
                List.of("/public/**"),
                true,
                List.of("gateway.internal"),
                List.of()
        )).hasMessageContaining("合法 IP 字面量");
    }

    @Test
    void redactsJwtSecretFromStringRepresentation() {
        AuthProperties properties = new AuthProperties(
                true,
                "JWT",
                "Authorization",
                "Bearer ",
                "jwt-secret-value-0123456789abcdef",
                7_200_000L,
                false,
                List.of("/public/**"),
                false,
                List.of(),
                List.of()
        );

        assertThat(properties.toString())
                .contains("jwtSecret=<redacted>")
                .doesNotContain("jwt-secret-value-0123456789abcdef");
    }
}
