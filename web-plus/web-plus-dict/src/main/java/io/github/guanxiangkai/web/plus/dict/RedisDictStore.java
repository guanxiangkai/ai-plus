package io.github.guanxiangkai.web.plus.dict;

import io.github.guanxiangkai.redis.plus.cache.ThreeLevelCacheTemplate;
import io.github.guanxiangkai.web.plus.core.model.DictItem;
import io.github.guanxiangkai.web.plus.core.spi.DictProvider;
import io.github.guanxiangkai.web.plus.core.spi.DictWriteSink;
import io.github.guanxiangkai.web.plus.dict.properties.DictProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Redis Plus 三级缓存的字典存储实现。
 * <p>
 * 将每个字典类型的完整 {@code value→label} 映射表缓存到三个层级：
 * </p>
 * <pre>
 * L1 — JVM 本地 Caffeine 缓存（短 TTL，亚微秒级读取）
 * L2 — Redis 分布式缓存（长 TTL，由 {@code web-plus.dict.ttl} 控制）
 * L3 — {@link DictProvider}（缓存未命中时提供字典数据）
 * </pre>
 *
 * <p>
 * 字典翻译是高读低写场景（每次 HTTP 响应可能翻译数十个字段），L1 命中后无需任何
 * Redis 网络开销。<br>
 * L1 的本地 TTL 由 {@code redis-plus.cache.local.ttl}（默认 5m）控制；
 * L2 的 Redis TTL 由 {@code web-plus.dict.ttl}（默认 24h）控制。
 * </p>
 *
 * <h3>缓存命名空间</h3>
 * <pre>
 * namespace : web-plus:dict
 * key       : {dictType}（如 "sys_status"）
 * value     : Map&lt;String, String&gt;（JSON，如 {"0":"禁用","1":"启用"}）
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 * @see DictWriteSink
 * @see DictTranslator
 * @see DictRefresher
 */
@Slf4j
public class RedisDictStore implements DictWriteSink {

    /** 缓存命名空间，作为所有字典 key 的前缀段 */
    static final String DICT_NAMESPACE = "web-plus:dict";

    private final ThreeLevelCacheTemplate cacheTemplate;
    private final DictProperties properties;
    private final DictProvider dictProvider;

    public RedisDictStore(ThreeLevelCacheTemplate cacheTemplate, DictProperties properties,
                          DictProvider dictProvider) {
        this.cacheTemplate = cacheTemplate;
        this.properties = properties;
        this.dictProvider = dictProvider;
    }

    // ──────────────────────────── 写入（DictWriteSink） ────────────────────────────

    /**
     * 将整个字典类型的 value→label 映射表写入三级缓存（L1 + L2）。
     * <p>
     * 写入前会驱逐该类型的缓存，使 L1 与 L2 均以本次数据为准。
     * </p>
     *
     * @param dictType 字典类型标识，如 {@code "sys_status"}
     * @param items    字典项列表
     */
    @Override
    public void put(String dictType, List<DictItem> items) {
        if (items == null || items.isEmpty()) {
            log.debug("[web-plus] 字典写入跳过（items 为空）: type={}", dictType);
            return;
        }

        Map<String, String> labelMap = new LinkedHashMap<>(items.size());
        for (DictItem item : items) {
            labelMap.put(item.value(), item.label());
        }

        // 先驱逐旧数据（含 L1 本地缓存），再写入新数据，避免 L1 持有过期 map
        cacheTemplate.evict(DICT_NAMESPACE, dictType);
        cacheTemplate.put(DICT_NAMESPACE, dictType, labelMap, properties.ttl());

        log.debug("[web-plus] 字典写入三级缓存: type={}, size={}", dictType, items.size());
    }

    // ──────────────────────────── 读取 ────────────────────────────────────────────

    /**
     * 查询指定字典类型中某个 value 对应的 label。
     * <p>
     * 查找顺序：L1（本地） → L2（Redis）→ 未命中则返回原始 value（字典未初始化）。
     * 整个 dictType 的 Map 会在 L1 命中后，同一请求内所有字段翻译均无 Redis 开销。
     * </p>
     *
     * @param dictType 字典类型
     * @param value    存储值（如 {@code "1"}）
     * @return 展示标签（如 {@code "启用"}）；未命中时原样返回 value
     */
    public String translate(String dictType, String value) {
        if (value == null) return null;

        Object cached = cacheTemplate.get(
                DICT_NAMESPACE, dictType, Map.class, properties.ttl(),
                // L3 loader：通过 DictProvider 从数据源加载字典数据
                k -> {
                    List<DictItem> items = dictProvider.provide(dictType);
                    if (items == null || items.isEmpty()) {
                        log.debug("[web-plus] DictProvider 未返回数据: type={}", dictType);
                        return null;
                    }
                    Map<String, String> map = new LinkedHashMap<>(items.size());
                    for (DictItem item : items) {
                        map.put(item.value(), item.label());
                    }
                    log.debug("[web-plus] DictProvider 加载字典: type={}, size={}", dictType, items.size());
                    return map;
                }
        );

        // null 或空标记 → 字典未初始化，返回原始值
        if (cached == null || cacheTemplate.isNullMarker(cached)) {
            log.debug("[web-plus] 字典未命中，返回原始值: type={}, value={}", dictType, value);
            return value;
        }

        if (!(cached instanceof Map<?, ?> labelMap)) {
            log.warn("[web-plus] 字典缓存类型异常，返回原始值: type={}, value={}, cachedType={}",
                    dictType, value, cached.getClass().getName());
            return value;
        }
        Object label = labelMap.get(value);
        return label instanceof String text ? text : value;
    }

    // ──────────────────────────── 缓存失效 ───────────────────────────────────────

    /**
     * 清除指定字典类型的 L1 + L2 缓存。
     *
     * @param dictType 字典类型标识（如 {@code "sys_status"}）
     */
    public void invalidate(String dictType) {
        cacheTemplate.evict(DICT_NAMESPACE, dictType);
        log.debug("[web-plus] 字典缓存已清除: type={}", dictType);
    }

    /**
     * 清除命名空间 {@value DICT_NAMESPACE} 下所有字典的 L1 + L2 缓存。
     */
    public void invalidateAll() {
        cacheTemplate.clear(DICT_NAMESPACE);
        log.debug("[web-plus] 所有字典缓存已清除");
    }
}
