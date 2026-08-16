package io.github.guanxiangkai.web.plus.core.properties;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ClientIpPropertiesTest {

    @Test
    void normalizesAndDeduplicatesTrustedProxyIps() {
        ClientIpProperties properties = new ClientIpProperties(List.of(
                " 203.0.113.8 ", "203.0.113.8", "2001:db8::1"));

        assertThat(properties.trustedProxyIps())
                .containsExactly("203.0.113.8", "2001:db8:0:0:0:0:0:1");
    }

    @Test
    void rejectsHostnamesAndCidrs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClientIpProperties(List.of("proxy.internal")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClientIpProperties(List.of("192.0.2.0/24")));
    }
}
