package io.github.guanxiangkai.jpa.plus.starter.dict;

import io.github.guanxiangkai.jpa.plus.field.dict.model.DictTranslateItem;
import io.github.guanxiangkai.jpa.plus.field.dict.spi.DictProvider;
import org.springframework.context.ApplicationListener;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 带 TTL 缓存的字典数据提供者装饰器
 *
 * <p>包装用户实现的 {@link DictProvider}，在应用层缓存字典数据，减少对数据库或 RPC 的重复调用。
 * 缓存项在超过 TTL 后下次访问时懒惰刷新（Lazy-Refresh）。</p>
 *
 * <h3>主动失效（Spring 事件）</h3>
 * <p>实现 {@link ApplicationListener}&lt;{@link DictCacheEvictEvent}&gt;，
 * 当业务方发布 {@link DictCacheEvictEvent} 时立即清除指定（或全部）缓存条目，
 * 无需等待 TTL 超期，适合字典数据发生变更后需要即时生效的场景。</p>
 *
 * <pre>{@code
 * // 失效单条字典
 * eventPublisher.publishEvent(DictCacheEvictEvent.of(this, "sys_user_sex"));
 *
 * // 失效全部字典缓存
 * eventPublisher.publishEvent(DictCacheEvictEvent.evictAll(this));
 * }</pre>
 *
 * <h3>配置方式</h3>
 * <pre>{@code
 * jpa-plus:
 *   dict:
 *     cache:
 *       enabled: true          # 默认 true（存在 DictProvider 时自动启用）
 *       ttl-seconds: 300       # 缓存有效期，默认 300 秒
 * }</pre>
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>使用 {@link ConcurrentHashMap} 保证线程安全（无锁竞争开销）</li>
 *   <li>到期后下次访问才刷新（Lazy-Refresh），避免后台线程复杂性</li>
 *   <li>通过 Spring 事件实现主动失效，不引入 Redis 等外部依赖</li>
 *   <li>多节点场景：消费 MQ 消息后在本地 {@code publishEvent} 即可实现集群失效</li>
 * </ul>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class CachedDictProvider implements DictProvider, ApplicationListener<DictCacheEvictEvent> {

    private final DictProvider delegate;
    private final long ttlMillis;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public CachedDictProvider(DictProvider delegate, long ttlSeconds) {
        this.delegate = delegate;
        this.ttlMillis = ttlSeconds * 1000L;
    }

    @Override
    public List<DictTranslateItem> getItems(String dictCode) {
        // 先读取有效缓存；缓存未命中时在 Map 锁外加载，避免数据源访问长时间占用分段锁。
        // 并发未命中可能重复加载，但最终会原子安装一份有效结果。
        CacheEntry existing = cache.get(dictCode);
        if (existing != null && !existing.isExpired()) {
            return existing.items();
        }
        List<DictTranslateItem> freshItems = delegate.getItems(dictCode);
        long expireAt = System.currentTimeMillis() + ttlMillis;
        CacheEntry installed = cache.compute(dictCode, (k, current) ->
                (current != null && !current.isExpired()) ? current : new CacheEntry(freshItems, expireAt));
        return installed.items();
    }

    @Override
    public Optional<String> getLabel(String dictCode, Object value) {
        // 复用缓存的 getItems 实现
        if (value == null) return Optional.empty();
        String strValue = String.valueOf(value);
        return getItems(dictCode).stream()
                .filter(item -> strValue.equals(item.value()))
                .map(DictTranslateItem::label)
                .findFirst();
    }

    @Override
    public Map<String, String> getLabels(String dictCode, Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return DictProvider.super.getLabels(dictCode, values);
    }

    // ─── 主动失效（Spring 事件） ───────────────────────────────────────────

    /**
     * 监听 {@link DictCacheEvictEvent}，收到事件后立即清除对应缓存。
     *
     * <p>无 dictCode（{@code evictAll}）时清除全部缓存；
     * 有 dictCode 时仅清除该编码的缓存条目。</p>
     */
    @Override
    public void onApplicationEvent(DictCacheEvictEvent event) {
        event.getDictCode().ifPresentOrElse(
                this::invalidate,
                this::invalidateAll
        );
    }

    /**
     * 清除指定字典编码的缓存
     */
    public void invalidate(String dictCode) {
        cache.remove(dictCode);
    }

    /**
     * 清除所有缓存
     */
    public void invalidateAll() {
        cache.clear();
    }

    // ─── 内部缓存条目 ───

    private record CacheEntry(List<DictTranslateItem> items, long expireAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
