package io.github.guanxiangkai.web.plus.license.runtime;

import io.github.guanxiangkai.web.plus.license.LicenseException;
import io.github.guanxiangkai.web.plus.license.LicenseKeys;
import io.github.guanxiangkai.web.plus.license.LicenseMode;
import io.github.guanxiangkai.web.plus.license.LicenseTokens;
import io.github.guanxiangkai.web.plus.license.properties.LicenseProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 应用生命周期内的授权状态；在线模式只保存当前进程中的已验证短期租约。 */
public final class LicenseRuntime implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LicenseRuntime.class);
    private final LicenseProperties properties;
    private final LicenseTokens tokens;
    private final LicenseGuard guard;
    private final LicenseLeaseClient client;
    private final SecureRandom random = new SecureRandom();
    private ScheduledExecutorService scheduler;
    private boolean started;
    private boolean closed;

    /** 创建校验环境；公钥来自预先信任的文件，读取失败将阻止自动装配。 */
    public LicenseRuntime(LicenseProperties properties) {
        properties.validate();
        this.properties = properties;
        Clock clock = Clock.systemUTC();
        this.tokens = new LicenseTokens(LicenseKeys.readPublicKey(properties.publicKey()), properties.issuer(),
                properties.subject(), properties.product(), properties.instance(), properties.mode(), clock);
        this.guard = new LicenseGuard(clock, System::nanoTime, properties.requiredFeatures());
        this.client = properties.mode() == LicenseMode.ONLINE ? new HttpsLicenseClient(properties) : null;
    }

    LicenseRuntime(LicenseProperties properties, LicenseTokens tokens, LicenseGuard guard, LicenseLeaseClient client) {
        this.properties = properties;
        this.tokens = tokens;
        this.guard = guard;
        this.client = client;
    }

    /** 启动时必须拿到有效授权；网络失败也不能带着空租约启动。 */
    public synchronized void start() {
        if (closed) throw new IllegalStateException("授权运行时已关闭");
        if (started) return;
        try {
            if (properties.mode() == LicenseMode.OFFLINE) {
                byte[] bytes;
                try (var input = Files.newInputStream(properties.licenseFile())) { bytes = input.readNBytes(65537); }
                if (bytes.length > 65536) throw new LicenseException("许可证超过大小限制");
                guard.accept(tokens.verify(new String(bytes, StandardCharsets.UTF_8).strip(), ""));
            } else {
                renew();
                scheduler = Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().daemon(true).name("license-renewal").factory());
                scheduler.scheduleWithFixedDelay(this::refresh, properties.refreshInterval().toMillis(),
                        properties.refreshInterval().toMillis(), TimeUnit.MILLISECONDS);
            }
            started = true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            close();
            throw new LicenseException("授权初始化被中断");
        } catch (IOException | RuntimeException failure) {
            close();
            throw new LicenseException("授权初始化失败");
        }
    }

    /** 返回本进程的统一授权关口。 */
    public LicenseGuard guard() { return guard; }

    private void renew() throws IOException, InterruptedException {
        byte[] challenge = new byte[32];
        random.nextBytes(challenge);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge);
        guard.accept(tokens.verify(client.fetch(nonce), nonce));
    }

    // 同步 close 与续租，确保关闭后任何在途响应都无法恢复授权状态。
    synchronized void refresh() {
        if (closed) return;
        try {
            renew();
        } catch (IOException temporaryFailure) {
            // 仅保留签名尚未过期的原租约，不延长有效期、不切换离线模式。
            LOGGER.warn("授权续租暂时失败；已有租约到期后将拒绝受保护操作");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            guard.invalidate();
        } catch (RuntimeException rejected) {
            guard.invalidate();
            LOGGER.warn("授权续租被拒绝或响应无效；已撤销本进程授权状态");
        }
    }

    /** 关闭续租并清空授权状态，不等待无界网络请求。 */
    @Override public synchronized void close() {
        closed = true;
        if (scheduler != null) scheduler.shutdownNow();
        if (client != null) client.close();
        guard.invalidate();
    }
}
