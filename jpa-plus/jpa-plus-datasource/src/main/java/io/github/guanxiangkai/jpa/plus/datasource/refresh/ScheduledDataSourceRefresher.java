package io.github.guanxiangkai.jpa.plus.datasource.refresh;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时数据源刷新器
 *
 * <p>周期性调用 {@link DataSourceRefresher#refresh()} 从配置源拉取最新数据源配置。
 * 底层通过 {@link ScheduledExecutorService} 实现定时调度，
 * 由 {@link io.github.guanxiangkai.jpa.plus.datasource.registry.DynamicDataSourceRegistry#reload()}
 * 自动处理差异化刷新（无变更时为空操作，开销极低）。</p>
 *
 * <h3>配置方式</h3>
 * <pre>{@code
 * jpa-plus:
 *   datasource:
 *     dynamic:
 *       schedule:
 *         enabled: true       # 开启定时刷新
 *         interval: 30s       # 轮询间隔（支持 Duration 格式：30s / 1m / PT30S）
 * }</pre>
 *
 * <h3>适用场景</h3>
 * <ul>
 *   <li>管理后台修改了数据源配置表，无需手动通知框架</li>
 *   <li>多实例部署，无事件总线（如 Redis Pub/Sub）时，作为兜底刷新机制</li>
 *   <li>配置中心不支持 push 模式时，使用 pull 模式定时拉取</li>
 * </ul>
 *
 * <p><b>线程安全：</b>使用守护线程调度，应用关停时自动退出。
 * 实现 {@link AutoCloseable}，支持优雅关闭。</p>
 *
 * @author guanxiangkai
 * @see DataSourceRefresher
 * @since 1.0.0
 */
@Slf4j
public class ScheduledDataSourceRefresher implements AutoCloseable {

    private final ScheduledExecutorService scheduler;

    /**
     * @param refresher 数据源刷新门面
     * @param interval  轮询间隔
     */
    public ScheduledDataSourceRefresher(DataSourceRefresher refresher, Duration interval) {
        Objects.requireNonNull(refresher, "refresher must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be greater than zero, got: " + interval);
        }

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jpa-plus-ds-refresh");
            t.setDaemon(true);
            return t;
        });

        long millis = interval.toMillis();
        this.scheduler.scheduleWithFixedDelay(() -> {
            try {
                refresher.refresh();
            } catch (Exception e) {
                log.error("Scheduled datasource refresh failed: {}", e.getMessage(), e);
            }
        }, millis, millis, TimeUnit.MILLISECONDS);

        log.info("Scheduled datasource refresh enabled, interval: {}", interval);
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Scheduled datasource refresh stopped");
    }
}
