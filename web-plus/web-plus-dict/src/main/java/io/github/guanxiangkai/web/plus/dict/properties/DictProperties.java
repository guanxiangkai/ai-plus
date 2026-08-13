package io.github.guanxiangkai.web.plus.dict.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 字典回显配置属性
 * <p>
 * 控制 web-plus 字典缓存行为。字典数据通过 redis-plus {@code ThreeLevelCacheTemplate}
 * 以三级缓存方式存储：
 * </p>
 * <pre>
 * L1 — JVM 本地 Caffeine（TTL 由 {@code redis-plus.cache.local.ttl} 控制，默认 5m）
 * L2 — Redis 分布式缓存（TTL 由本配置 {@code ttl} 字段控制，默认 24h）
 * L3 — CacheLoader（返回 null，字典由 DictWriter SPI 主动推入）
 * </pre>
 *
 * <p>每个字典类型（如 {@code "sys_status"}）的完整 value→label Map 作为一个整体存储，
 * 同一请求内多字段翻译仅需一次 L1 本地查找，无 Redis 开销。</p>
 *
 * <pre>
 * web-plus:
 *   dict:
 *     enabled: true          # 是否启用字典回显（默认 true，需要 redis-plus-starter）
 *     key-prefix: dict       # 文档用途字段（实际 key 由 ThreeLevelCacheTemplate 管理）
 *     ttl: 24h               # L2 Redis 缓存 TTL（默认 24 小时）
 *     fail-fast: false       # 启动初始化失败是否阻断应用（默认 false）
 *
 * redis-plus:
 *   cache:
 *     local:
 *       ttl: 5m              # L1 本地缓存 TTL（默认 5 分钟）
 *       maximum-size: 10000  # L1 最大缓存条目数
 * </pre>
 *
 * @param enabled   是否启用字典缓存功能（默认 {@code true}）
 * @param keyPrefix 文档标识字段，标记字典数据所属命名空间（默认 {@code "dict"}）；
 *                  实际 Redis key 格式由 {@code ThreeLevelCacheTemplate} 内部管理
 * @param ttl       L2 Redis 缓存过期时间（默认 24 小时）；设为 {@code null} 则永不过期
 * @param failFast  初始化时 {@code DictWriter} 执行异常是否中断启动（默认 {@code false}）
 * @author guanxiangkai
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "web-plus.dict")
public record DictProperties(
        Boolean enabled,
        String keyPrefix,
        Duration ttl,
        Boolean failFast
) {

    public DictProperties {
        if (enabled == null) enabled = true;
        if (keyPrefix == null || keyPrefix.isBlank()) keyPrefix = "dict";
        if (ttl == null) ttl = Duration.ofHours(24);
        if (failFast == null) failFast = false;
    }

    /**
     * 构建完整 Redis key（保留，供文档说明用，实际存储由 ThreeLevelCacheTemplate 管理）：
     * {@code web-plus:{keyPrefix}:{dictType}}
     *
     * @param dictType 字典类型标识
     * @return Redis key 示例
     */
    public String buildKey(String dictType) {
        return "web-plus:" + keyPrefix + ":" + dictType;
    }

    /**
     * 构建扫描匹配模式（保留，供文档说明用）：
     * {@code web-plus:{keyPrefix}:*}
     *
     * @return Redis SCAN 的 match pattern 示例
     */
    public String buildKeyPattern() {
        return "web-plus:" + keyPrefix + ":*";
    }
}
