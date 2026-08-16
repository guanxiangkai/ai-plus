package io.github.guanxiangkai.web.plus.core.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SecurityFingerprintTest {

    @Test
    void returnsDeterministicFixedLengthFingerprintWithoutExposingInput() {
        String material = "high-entropy-token-value";

        assertThat(SecurityFingerprint.sha256(material))
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(SecurityFingerprint.sha256(material))
                .doesNotContain(material);
    }

    @Test
    void rejectsNullInput() {
        assertThatNullPointerException()
                .isThrownBy(() -> SecurityFingerprint.sha256(null))
                .withMessageContaining("不能为空");
    }
}
