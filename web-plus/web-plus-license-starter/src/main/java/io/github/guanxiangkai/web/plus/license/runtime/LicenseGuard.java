package io.github.guanxiangkai.web.plus.license.runtime;

import io.github.guanxiangkai.web.plus.license.LicenseClaims;
import io.github.guanxiangkai.web.plus.license.LicenseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** 线程安全的授权关口；HTTP、定时任务及消息消费均应在执行受保护操作前检查。 */
public final class LicenseGuard {
    private final Clock clock;
    private final LongSupplier ticker;
    private final Set<String> requiredFeatures;
    private final AtomicReference<Grant> current = new AtomicReference<>();

    LicenseGuard(Clock clock, LongSupplier ticker, Set<String> requiredFeatures) {
        this.clock = clock;
        this.ticker = ticker;
        this.requiredFeatures = Set.copyOf(requiredFeatures);
    }

    void accept(LicenseClaims claims) {
        Instant now = clock.instant();
        if (!claims.features().containsAll(requiredFeatures) || now.isBefore(claims.notBefore())
                || !now.isBefore(claims.expiresAt())) {
            throw new LicenseException("许可证不满足当前应用运行要求");
        }
        long duration;
        try { duration = Duration.between(now, claims.expiresAt()).toNanos(); }
        catch (ArithmeticException overflow) { duration = Long.MAX_VALUE; }
        current.set(new Grant(claims, ticker.getAsLong(), duration));
    }

    void invalidate() { current.set(null); }

    /** 返回当前有效许可证；过期、撤销或不可用时抛出 LicenseException。 */
    public LicenseClaims current() {
        Grant grant = current.get();
        Instant now = clock.instant();
        if (grant == null || now.isBefore(grant.claims().issuedAt()) || now.isBefore(grant.claims().notBefore())
                || !now.isBefore(grant.claims().expiresAt())
                || ticker.getAsLong() - grant.loadedAt() >= grant.validNanos()) {
            throw new LicenseException("当前没有有效授权");
        }
        return grant.claims();
    }

    /** 检查指定功能；许可证中的功能名精确匹配，不支持隐式通配符。 */
    public void requireFeature(String feature) {
        if (feature == null || feature.isBlank() || !current().features().contains(feature)) {
            throw new LicenseException("当前功能未授权");
        }
    }

    private record Grant(LicenseClaims claims, long loadedAt, long validNanos) {}
}
