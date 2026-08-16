package io.github.guanxiangkai.web.plus.security.model;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityModelRedactionTest {

    @Test
    void shouldRedactLoginResultSecrets() {
        LoginResult result = new LoginResult(
                "access-token-secret",
                1_000L,
                "Bearer",
                CurrentUser.ofUserId("user-secret")
        );

        assertThat(result.toString())
                .contains("<redacted>")
                .doesNotContain("access-token-secret", "user-secret");
    }
}
