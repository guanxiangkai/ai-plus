package io.github.guanxiangkai.web.plus.security.password;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import io.github.guanxiangkai.web.plus.security.config.SecurityAutoConfiguration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProtocolPasswordEncoderTest {

    private final ProtocolPasswordEncoder encoder = new ProtocolPasswordEncoder();

    @Test
    void encodesAndMatchesOnlyProtocolDigest() {
        String digest = PasswordProtocol.sha1Utf8("test-password");
        String encoded = encoder.encode(digest);

        assertThat(encoded).startsWith("$2");
        assertThat(encoder.matches(digest, encoded)).isTrue();
        assertThat(encoder.matches(PasswordProtocol.sha1Utf8("wrong-password"), encoded)).isFalse();
    }

    @Test
    void rejectsRawPasswordWrongEncodingAndDoubleHash() {
        String digest = PasswordProtocol.sha1Utf8("test-password");
        String encoded = encoder.encode(digest);

        assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode("test-password"));
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode(digest.toUpperCase(Locale.ROOT)));
        assertThat(encoder.matches("test-password", encoded)).isFalse();
        assertThat(encoder.matches(PasswordProtocol.sha1Utf8(digest), encoded)).isFalse();
    }

    @Test
    void rejectsDigestOfAnEmptyPassword() {
        String emptyPasswordDigest = PasswordProtocol.sha1Utf8("");

        assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode(emptyPasswordDigest));
    }

    @Test
    void rejectsBcryptHashWithAnUnsupportedCost() {
        assertThat(PasswordProtocol.isBcryptHash("$2b$04$" + "A".repeat(53))).isTrue();
        assertThat(PasswordProtocol.isBcryptHash("$2b$31$" + "A".repeat(53))).isTrue();
        assertThat(PasswordProtocol.isBcryptHash("$2b$03$" + "A".repeat(53))).isFalse();
        assertThat(PasswordProtocol.isBcryptHash("$2b$32$" + "A".repeat(53))).isFalse();
    }

    @Test
    void acceptsExistingStandardBcryptAndUsesRandomSalt() {
        String digest = PasswordProtocol.sha1Utf8("test-password");
        String first = encoder.encode(digest);
        String second = encoder.encode(digest);
        String existingBcrypt = new BCryptPasswordEncoder().encode(digest);

        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches(digest, first)).isTrue();
        assertThat(encoder.matches(digest, existingBcrypt)).isTrue();
        assertThat(encoder.matches(digest, "{sha1-bcrypt}" + first)).isFalse();
    }

    @Test
    void passwordProtocolRejectsNullWhitespaceAndWrongLengthWithoutNormalization() {
        String password = PasswordProtocol.sha1Utf8("test-password");
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode(null));
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode(" " + password));
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode(password.substring(1)));
        assertThat(encoder.matches(null, "$2a$10$" + "a".repeat(53))).isFalse();
        assertThat(encoder.matches(password, null)).isFalse();
        assertThat(encoder.upgradeEncoding(null)).isTrue();
    }

    @Test
    void protocolUsesExpectedDigestAndDelegatesBcryptCostUpgrade() {
        assertThat(PasswordProtocol.sha1Utf8("abc"))
                .isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d");
        String password = PasswordProtocol.sha1Utf8("test-password");
        String weakHash = new BCryptPasswordEncoder(4).encode(password);
        assertThat(encoder.matches(password, weakHash)).isTrue();
        assertThat(encoder.upgradeEncoding(weakHash)).isTrue();
        assertThat(encoder.upgradeEncoding(encoder.encode(password))).isFalse();
    }

    @Test
    void defaultSecurityEncoderStillAcceptsRawPasswordWithoutOptingIntoProtocol() {
        var defaults = new SecurityAutoConfiguration().passwordEncoder();
        String encoded = defaults.encode("raw-test-password");

        assertThat(defaults).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(defaults.matches("raw-test-password", encoded)).isTrue();
        assertThat(defaults.matches(PasswordProtocol.sha1Utf8("raw-test-password"), encoded)).isFalse();
    }
}
