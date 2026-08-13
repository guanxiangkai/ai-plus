package io.github.guanxiangkai.web.plus.web.properties;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsPropertiesTest {

    @Test
    void defaultsRemainNonCredentialed() {
        CorsProperties properties = new CorsProperties(null, null, null, null, null, null, null, null);

        assertThat(properties.allowCredentials()).isFalse();
        assertThat(properties.allowedOrigins()).isEmpty();
    }

    @Test
    void rejectsCredentialedWildcardOrigins() {
        assertThatThrownBy(() -> new CorsProperties(
                true,
                List.of("*"),
                List.of(),
                List.of("GET"),
                List.of("*"),
                List.of(),
                true,
                1800L
        )).hasMessageContaining("allow-credentials=true");
    }

    @Test
    void rejectsCredentialedWildcardOriginPatterns() {
        assertThatThrownBy(() -> new CorsProperties(
                true,
                List.of("https://app.example.com"),
                List.of("*"),
                List.of("GET"),
                List.of("*"),
                List.of(),
                true,
                1800L
        )).hasMessageContaining("allowed-origin-patterns");
    }
}
