package io.github.guanxiangkai.web.plus.core.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedForwardPropertiesTest {

    @Test
    void shouldRedactForwardTokenFromStringRepresentation() {
        TrustedForwardProperties properties = new TrustedForwardProperties();
        properties.setToken("forward-token-secret");

        assertThat(properties.toString())
                .doesNotContain("forward-token-secret");
    }
}
