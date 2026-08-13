package io.github.guanxiangkai.web.plus.security.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SuperAdminAuthorizationManagerFactoryTest {

    @Test
    void shouldBypassAuthorityChecksForSuperAdminClaim() {
        SuperAdminAuthorizationManagerFactory<Object> factory = new SuperAdminAuthorizationManagerFactory<>();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("admin", null, List.of());
        authentication.setDetails(Map.of("superAdmin", true));

        var result = factory.hasAuthority("system:role:delete")
                .authorize(() -> authentication, new Object());

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isTrue();
    }

    @Test
    void shouldKeepExactAuthorityChecksForNormalUsers() {
        SuperAdminAuthorizationManagerFactory<Object> factory = new SuperAdminAuthorizationManagerFactory<>();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("user", null, List.of());
        authentication.setDetails(Map.of("superAdmin", false));

        var result = factory.hasAuthority("system:role:delete")
                .authorize(() -> authentication, new Object());

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
    }
}
