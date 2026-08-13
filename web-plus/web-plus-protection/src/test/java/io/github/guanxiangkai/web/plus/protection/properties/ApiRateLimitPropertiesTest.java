package io.github.guanxiangkai.web.plus.protection.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** API 限流默认配置测试。 */
class ApiRateLimitPropertiesTest {
    @Test
    void shouldLimitAllApplicationEndpointsByDefaultWithoutAgentExemptions() {
        ApiRateLimitProperties properties = new ApiRateLimitProperties(false, 0, -1, null, null, null);

        assertThat(properties.includePaths()).containsExactly("/**");
        assertThat(properties.excludePaths())
                .contains("/auth/login", "/auth/refresh", "/internal/**", "/actuator/health")
                .doesNotContain("/agent/session/ask", "/agent/speech/transcribe");
        assertThat(properties.limit()).isEqualTo(120);
        assertThat(properties.burst()).isZero();
    }
}
