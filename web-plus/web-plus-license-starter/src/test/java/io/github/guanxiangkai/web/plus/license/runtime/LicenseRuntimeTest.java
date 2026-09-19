package io.github.guanxiangkai.web.plus.license.runtime;

import io.github.guanxiangkai.web.plus.license.LicenseClaims;
import io.github.guanxiangkai.web.plus.license.LicenseException;
import io.github.guanxiangkai.web.plus.license.LicenseMode;
import io.github.guanxiangkai.web.plus.license.LicenseTokens;
import io.github.guanxiangkai.web.plus.license.autoconfigure.LicenseAutoConfiguration;
import io.github.guanxiangkai.web.plus.license.properties.LicenseProperties;
import io.github.guanxiangkai.web.plus.license.web.LicenseWebFilter;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证许可证在启动、续租、到期和请求入口的实际拒绝路径。 */
class LicenseRuntimeTest {
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");
    private static KeyPair keys;
    @TempDir Path directory;

    @BeforeAll static void createTestKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        keys = generator.generateKeyPair();
    }

    @Test void onlineStartupRequiresAuthorityAndEveryRenewalUsesNewNonce() {
        MutableClock clock = new MutableClock(NOW);
        LicenseGuard guard = new LicenseGuard(clock, () -> 0L, Set.of("reports"));
        FakeClient client = new FakeClient();
        try (LicenseRuntime runtime = runtime(clock, guard, client)) {
            runtime.start();
            guard.requireFeature("reports");
            runtime.refresh();
            assertThat(client.nonces).hasSize(2);
            assertThrows(LicenseException.class, () -> guard.requireFeature("unlicensed"));
        }
        assertThrows(LicenseException.class, guard::current);
    }

    @Test void temporaryFailureNeverExtendsLeaseButAuthorityDenialInvalidatesImmediately() {
        MutableClock clock = new MutableClock(NOW);
        AtomicLong ticks = new AtomicLong();
        LicenseGuard guard = new LicenseGuard(clock, ticks::get, Set.of());
        FakeClient client = new FakeClient();
        try (LicenseRuntime runtime = runtime(clock, guard, client)) {
            runtime.start();
            client.temporaryFailure = true;
            runtime.refresh();
            assertDoesNotThrow(guard::current);
            clock.now = NOW.plusSeconds(301);
            ticks.set(Duration.ofSeconds(301).toNanos());
            assertThrows(LicenseException.class, guard::current);
        }
        clock.now = NOW;
        ticks.set(0L);
        FakeClient denied = new FakeClient();
        try (LicenseRuntime runtime = runtime(clock, guard, denied)) {
            runtime.start();
            denied.reject = true;
            runtime.refresh();
            assertThrows(LicenseException.class, guard::current);
        }
    }

    @Test void wrongNonceCannotReplayAnEarlierLease() {
        MutableClock clock = new MutableClock(NOW);
        LicenseGuard guard = new LicenseGuard(clock, () -> 0L, Set.of());
        FakeClient client = new FakeClient();
        try (LicenseRuntime runtime = runtime(clock, guard, client)) {
            runtime.start();
            client.replay = true;
            runtime.refresh();
            assertThrows(LicenseException.class, guard::current);
        }
    }

    @Test void unavailableAuthorityPreventsStartupAndCannotFallBackToOffline() {
        MutableClock clock = new MutableClock(NOW);
        LicenseGuard guard = new LicenseGuard(clock, () -> 0L, Set.of());
        FakeClient client = new FakeClient();
        client.temporaryFailure = true;
        try (LicenseRuntime runtime = runtime(clock, guard, client)) {
            assertThrows(LicenseException.class, runtime::start);
            assertThrows(LicenseException.class, guard::current);
            assertTrue(client.closed);
        }
    }

    @Test void monotonicDeadlinePreventsClockRollbackFromExtendingCurrentLease() {
        MutableClock clock = new MutableClock(NOW);
        AtomicLong ticks = new AtomicLong();
        LicenseGuard guard = new LicenseGuard(clock, ticks::get, Set.of());
        guard.accept(claims(LicenseMode.OFFLINE, "", NOW));
        ticks.set(Duration.ofMinutes(6).toNanos());
        // 墙钟仍落在许可证有效期，进程单调时间已证明租约到期。
        clock.now = NOW.plusSeconds(1);
        assertThrows(LicenseException.class, guard::current);
    }

    @Test void offlineStartupAndFeatureCheckUseSignedFile() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        LicenseGuard guard = new LicenseGuard(clock, () -> 0L, Set.of("reports"));
        Path license = directory.resolve("business.jwt");
        Files.writeString(license, LicenseTokens.issue(claims(LicenseMode.OFFLINE, "", NOW), keys.getPrivate()));
        LicenseProperties properties = properties(LicenseMode.OFFLINE, license);
        try (LicenseRuntime runtime = new LicenseRuntime(properties, verifier(clock, LicenseMode.OFFLINE), guard, null)) {
            runtime.start();
            guard.requireFeature("reports");
            clock.now = NOW.plusSeconds(300);
            assertThrows(LicenseException.class, guard::current);
        }
    }

    @Test void webFilterRejectsExpiredLicenseBeforeExecutingHandler() {
        MutableClock clock = new MutableClock(NOW);
        LicenseGuard guard = new LicenseGuard(clock, () -> 0L, Set.of());
        guard.accept(claims(LicenseMode.OFFLINE, "", NOW));
        LicenseWebFilter filter = new LicenseWebFilter(guard);
        AtomicBoolean invoked = new AtomicBoolean();
        var valid = MockServerWebExchange.from(MockServerHttpRequest.get("/reports"));
        filter.filter(valid, exchange -> { invoked.set(true); return Mono.empty(); }).block();
        assertTrue(invoked.get());
        invoked.set(false);
        clock.now = NOW.plusSeconds(300);
        var expired = MockServerWebExchange.from(MockServerHttpRequest.get("/reports"));
        filter.filter(expired, exchange -> { invoked.set(true); return Mono.empty(); }).block();
        assertThat(expired.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(invoked).isFalse();
    }

    @Test void autoconfigurationRequiresConfigurationAndAcceptsValidOfflineLicense() throws Exception {
        var runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LicenseAutoConfiguration.class));
        runner.run(context -> assertThat(context).hasFailed());
        Path publicKey = Files.write(directory.resolve("public.der"), keys.getPublic().getEncoded());
        Instant now = Instant.now().minusSeconds(1);
        Path license = Files.writeString(directory.resolve("context.jwt"),
                LicenseTokens.issue(claims(LicenseMode.OFFLINE, "", now), keys.getPrivate()));
        runner.withPropertyValues("web-plus.license.mode=OFFLINE", "web-plus.license.issuer=issuer",
                "web-plus.license.subject=customer", "web-plus.license.product=business", "web-plus.license.instance=instance",
                "web-plus.license.public-key=" + publicKey, "web-plus.license.license-file=" + license)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(LicenseGuard.class);
                    context.getBean(LicenseGuard.class).requireFeature("reports");
                });
    }

    @Test void bodyReaderCancelsOversizedResponseInsteadOfAllocatingIt() {
        var subscriber = new HttpsLicenseClient.BoundedBody();
        AtomicBoolean cancelled = new AtomicBoolean();
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override public void request(long count) {}
            @Override public void cancel() { cancelled.set(true); }
        });
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[65537])));
        assertTrue(cancelled.get());
        assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
    }

    @Test void rejectsHttpAndMixedModeConfiguration() {
        LicenseProperties online = new LicenseProperties(LicenseMode.ONLINE, "issuer", "customer", "business", "instance",
                Path.of("public.pem"), null, URI.create("http://license.example/lease"), Path.of("credential"), null, null, Set.of());
        assertThrows(IllegalArgumentException.class, online::validate);
        LicenseProperties mixed = new LicenseProperties(LicenseMode.OFFLINE, "issuer", "customer", "business", "instance",
                Path.of("public.pem"), Path.of("license.jwt"), URI.create("https://license.example/lease"), null, null, null, Set.of());
        assertThrows(IllegalArgumentException.class, mixed::validate);
    }

    private LicenseRuntime runtime(Clock clock, LicenseGuard guard, FakeClient client) {
        return new LicenseRuntime(properties(LicenseMode.ONLINE, null), verifier(clock, LicenseMode.ONLINE), guard, client);
    }

    private LicenseTokens verifier(Clock clock, LicenseMode mode) {
        return new LicenseTokens(keys.getPublic(), "issuer", "customer", "business", "instance", mode, clock);
    }

    private LicenseProperties properties(LicenseMode mode, Path file) {
        return new LicenseProperties(mode, "issuer", "customer", "business", "instance", directory.resolve("public.pem"), file,
                mode == LicenseMode.ONLINE ? URI.create("https://license.example/lease") : null,
                mode == LicenseMode.ONLINE ? directory.resolve("credential") : null, Duration.ofMinutes(1), Duration.ofSeconds(5), Set.of());
    }

    private static LicenseClaims claims(LicenseMode mode, String nonce, Instant now) {
        return new LicenseClaims("license", "issuer", "customer", "business", "instance", mode, now, now,
                now.plusSeconds(300), Set.of("reports"), nonce);
    }

    private static final class FakeClient implements LicenseLeaseClient {
        private final Set<String> nonces = new HashSet<>();
        private boolean temporaryFailure;
        private boolean reject;
        private boolean replay;
        private boolean closed;
        private String priorToken;
        @Override public String fetch(String nonce) throws IOException {
            nonces.add(nonce);
            if (temporaryFailure) throw new IOException("test transport failure");
            if (reject) throw new LicenseException("test denied");
            if (replay) return priorToken;
            priorToken = LicenseTokens.issue(claims(LicenseMode.ONLINE, nonce, NOW), keys.getPrivate());
            return priorToken;
        }
        @Override public void close() { closed = true; }
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
