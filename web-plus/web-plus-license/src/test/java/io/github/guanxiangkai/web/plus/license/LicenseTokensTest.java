package io.github.guanxiangkai.web.plus.license;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LicenseTokensTest {
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static KeyPair keyPair;
    private static KeyPair otherKeyPair;

    @BeforeAll
    static void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        keyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
    }

    @Test
    void issuesAndVerifiesOfflineLicense() {
        LicenseClaims claims = offlineClaims();
        LicenseClaims verified = verifier(keyPair).verify(LicenseTokens.issue(claims, keyPair.getPrivate()), "");
        assertEquals(claims, verified);
    }

    @Test
    void issuesAndVerifiesOfflineEmptyNonceAndFeatures() {
        LicenseClaims claims = new LicenseClaims("id", "issuer", "subject", "product", "instance", LicenseMode.OFFLINE,
                NOW.minusSeconds(1), NOW.minusSeconds(1), NOW.plusSeconds(60), Set.of(), "");
        assertEquals(claims, verifier(keyPair).verify(LicenseTokens.issue(claims, keyPair.getPrivate()), ""));
    }

    @Test
    void rejectsWrongKeyAndFixedContext() {
        String token = LicenseTokens.issue(offlineClaims(), keyPair.getPrivate());
        assertThrows(LicenseException.class, () -> verifier(otherKeyPair).verify(token, ""));
        assertThrows(LicenseException.class, () -> new LicenseTokens(keyPair.getPublic(), "other", "subject", "product", "instance", LicenseMode.OFFLINE, CLOCK).verify(token, ""));
        assertThrows(LicenseException.class, () -> new LicenseTokens(keyPair.getPublic(), "issuer", "subject", "other", "instance", LicenseMode.OFFLINE, CLOCK).verify(token, ""));
        assertThrows(LicenseException.class, () -> new LicenseTokens(keyPair.getPublic(), "issuer", "subject", "product", "other", LicenseMode.OFFLINE, CLOCK).verify(token, ""));
        assertThrows(LicenseException.class, () -> new LicenseTokens(keyPair.getPublic(), "issuer", "subject", "product", "instance", LicenseMode.ONLINE, CLOCK).verify(token, "nonce-for-current-request"));
    }

    @Test
    void rejectsTemporalAndMissingClaims() {
        LicenseClaims expired = new LicenseClaims("id", "issuer", "subject", "product", "instance", LicenseMode.OFFLINE,
                NOW.minusSeconds(120), NOW.minusSeconds(120), NOW.minusSeconds(1), Set.of(), "");
        assertThrows(LicenseException.class, () -> verifier(keyPair).verify(LicenseTokens.issue(expired, keyPair.getPrivate()), ""));

        String missingIssuedAt = Jwts.builder().header().type("ai-plus-license+jwt").and()
                .id("id").issuer("issuer").subject("subject").audience().add("product").and()
                .notBefore(Date.from(NOW.minusSeconds(1))).expiration(Date.from(NOW.plusSeconds(60)))
                .claim("instance", "instance").claim("mode", "OFFLINE").claim("features", List.of()).claim("nonce", "")
                .signWith(keyPair.getPrivate(), Jwts.SIG.PS256).compact();
        assertThrows(LicenseException.class, () -> verifier(keyPair).verify(missingIssuedAt, ""));
    }

    @Test
    void rejectsNonPs256CompressedNonceMismatchAndOversizedToken() {
        String rs256 = Jwts.builder().header().type("ai-plus-license+jwt").and().id("id").issuer("issuer").subject("subject")
                .audience().add("product").and().issuedAt(Date.from(NOW)).notBefore(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(60)))
                .claim("instance", "instance").claim("mode", "OFFLINE").claim("features", List.of()).claim("nonce", "")
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256).compact();
        assertThrows(LicenseException.class, () -> verifier(keyPair).verify(rs256, ""));

        String token = LicenseTokens.issue(offlineClaims(), keyPair.getPrivate());
        String zipHeader = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"PS256\",\"typ\":\"ai-plus-license+jwt\",\"zip\":\"DEF\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(LicenseException.class, () -> verifier(keyPair).verify(zipHeader + token.substring(token.indexOf('.')), ""));
        assertThrows(LicenseException.class, () -> verifier(keyPair).verify("a".repeat(64 * 1024 + 1), ""));

        LicenseClaims online = new LicenseClaims("id", "issuer", "subject", "product", "instance", LicenseMode.ONLINE,
                NOW, NOW, NOW.plusSeconds(60), Set.of("feature"), "nonce-for-current-request");
        String onlineToken = LicenseTokens.issue(online, keyPair.getPrivate());
        LicenseTokens onlineVerifier = new LicenseTokens(keyPair.getPublic(), "issuer", "subject", "product", "instance", LicenseMode.ONLINE, CLOCK);
        assertThrows(LicenseException.class, () -> onlineVerifier.verify(onlineToken, "another-request-nonce"));
    }

    @Test
    void rejectsOnlineLifetimeOverFifteenMinutes() {
        String token = Jwts.builder().header().type("ai-plus-license+jwt").and()
                .id("id").issuer("issuer").subject("subject").audience().add("product").and()
                .issuedAt(Date.from(NOW)).notBefore(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(901)))
                .claim("instance", "instance").claim("mode", "ONLINE").claim("features", List.of()).claim("nonce", "nonce-for-current-request")
                .signWith(keyPair.getPrivate(), Jwts.SIG.PS256).compact();
        LicenseTokens onlineVerifier = new LicenseTokens(keyPair.getPublic(), "issuer", "subject", "product", "instance", LicenseMode.ONLINE, CLOCK);
        assertThrows(LicenseException.class, () -> onlineVerifier.verify(token, "nonce-for-current-request"));
    }

    private static LicenseClaims offlineClaims() {
        return new LicenseClaims("id", "issuer", "subject", "product", "instance", LicenseMode.OFFLINE,
                NOW.minusSeconds(1), NOW.minusSeconds(1), NOW.plusSeconds(60), Set.of("feature"), "");
    }

    private static LicenseTokens verifier(KeyPair pair) {
        return new LicenseTokens(pair.getPublic(), "issuer", "subject", "product", "instance", LicenseMode.OFFLINE, CLOCK);
    }
}
