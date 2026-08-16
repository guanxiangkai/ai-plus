package io.github.guanxiangkai.web.plus.core.context;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserRedactionTest {

    @Test
    void shouldRedactIdentityAndAuthorizationFromStringRepresentation() {
        CurrentUser currentUser = new CurrentUser(
                "user-secret",
                "nickname-secret",
                "tenant-secret",
                "department-secret",
                Set.of("department-scope-secret"),
                Set.of("role-secret"),
                Set.of("permission-secret"),
                false,
                "device-secret",
                1_000L,
                Map.of("claim-secret", "claim-value-secret")
        );

        assertThat(currentUser.toString())
                .contains("identity=<redacted>", "authorization=<redacted>")
                .doesNotContain(
                        "user-secret", "nickname-secret", "tenant-secret", "department-secret",
                        "department-scope-secret", "role-secret", "permission-secret", "device-secret",
                        "claim-secret", "claim-value-secret");
    }
}
