package io.github.guanxiangkai.web.plus.dict;

import io.github.guanxiangkai.web.plus.core.spi.DictWriter;
import io.github.guanxiangkai.web.plus.dict.properties.DictProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * 字典刷新器
 * <p>
 * 触发所有已注册的 {@link DictWriter} SPI 实现将字典数据重新写入 Redis。
 * 框架在应用启动完成（{@code ApplicationReadyEvent}）后自动调用一次 {@link #refresh()}；
 * 业务侧也可注入此 Bean，在字典数据变更后手动触发刷新。
 * </p>
 *
 * <h3>业务侧用法</h3>
 * <pre>{@code
 * @Autowired
 * private DictRefresher dictRefresher;
 *
 * // 字典数据更新后触发全量刷新
 * public void updateDict(SysDict dict) {
 *     dictRepository.save(dict);
 *     dictRefresher.refresh();
 * }
 *
 * // 仅刷新特定 DictWriter
 * public void refreshSysDict() {
 *     dictRefresher.refresh(SysDictWriter.class);
 * }
 *
 * // 手动清除某类型缓存（如：业务侧实时更新后失效）
 * public void invalidateSysStatus() {
 *     dictRefresher.invalidate("sys_status");
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 * @see DictWriter
 * @see RedisDictStore
 */
@Slf4j
public class DictRefresher {

    private final List<DictWriter> writers;
    private final RedisDictStore dictStore;
    private final DictProperties properties;
    private final StringRedisTemplate redisTemplate;

    public DictRefresher(List<DictWriter> writers, RedisDictStore dictStore,
                         DictProperties properties, StringRedisTemplate redisTemplate) {
        this.writers = writers;
        this.dictStore = dictStore;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    // ──────────────────────────── 刷新 ───────────────────────────────────────────

    /**
     * 全量刷新：依次调用所有已注册的 {@link DictWriter}，将字典数据写入 Redis。
     * <p>
     * 当 {@code web-plus.dict.fail-fast=true} 时，任意 {@code DictWriter} 执行失败
     * 将抛出 {@link IllegalStateException} 并中断后续写入；
     * 默认（{@code fail-fast=false}）仅打印错误日志并继续。
     * </p>
     */
    public void refresh() {
        if (writers.isEmpty()) {
            log.debug("[web-plus] 未检测到 DictWriter 实现，跳过字典缓存初始化");
            return;
        }
        log.info("[web-plus] 开始刷新字典缓存，共 {} 个 DictWriter", writers.size());
        for (DictWriter writer : writers) {
            try {
                writer.write(dictStore);
                log.debug("[web-plus] DictWriter 执行成功: {}", writer.getClass().getSimpleName());
            } catch (Exception e) {
                if (Boolean.TRUE.equals(properties.failFast())) {
                    throw new IllegalStateException(
                            "[web-plus] 字典写入失败（failFast=true），Writer: " + writer.getClass().getName(), e);
                }
                log.error("[web-plus] DictWriter 执行失败，已跳过: {}", writer.getClass().getName(), e);
            }
        }
        log.info("[web-plus] 字典缓存刷新完成");
        publishRefreshEvent(DictChangeListener.ALL_MARKER);
    }

    /**
     * 仅刷新指定 {@link DictWriter} 实现类对应的字典数据。
     *
     * @param writerClass 目标 DictWriter 实现类
     */
    public void refresh(Class<? extends DictWriter> writerClass) {
        writers.stream()
                .filter(w -> writerClass.isAssignableFrom(w.getClass()))
                .findFirst()
                .ifPresentOrElse(
                        w -> {
                            w.write(dictStore);
                            log.info("[web-plus] 字典刷新完成: {}", writerClass.getSimpleName());
                        },
                        () -> log.warn("[web-plus] 未找到匹配的 DictWriter: {}", writerClass.getName())
                );
    }

    // ──────────────────────────── 缓存失效 ───────────────────────────────────────

    /**
     .     * 清除指定字典类型的 Redis 缓存，并通知其他实例刷新。
     *
     * @param dictType 字典类型标识（如 {@code "sys_status"}）
     */
    public void invalidate(String dictType) {
        dictStore.invalidate(dictType);
        publishRefreshEvent(dictType);
    }

    /**
     * 清除所有字典缓存（适合整体重新初始化前使用），并通知其他实例刷新。
     */
    public void invalidateAll() {
        dictStore.invalidateAll();
        publishRefreshEvent(DictChangeListener.ALL_MARKER);
    }

    // ──────────────────────────── Pub/Sub 通知 ─────────────────────────────────────

    /**
     * 通过 Redis Pub/Sub 发布字典变更通知，其他实例的 {@link DictChangeListener} 收到后
     * 自动失效本地 L1 缓存。
     *
     * @param body 字典类型编码，或 {@code "*"} 表示全量刷新
     */
    private void publishRefreshEvent(String body) {
        try {
            redisTemplate.convertAndSend(DictChangeListener.CHANNEL, body);
            log.debug("[web-plus] 已发布字典变更通知: channel={}, body={}", DictChangeListener.CHANNEL, body);
        } catch (Exception e) {
            log.warn("[web-plus] 发布字典变更通知失败: exception={}",
                    e.getClass().getSimpleName());
        }
    }
}
