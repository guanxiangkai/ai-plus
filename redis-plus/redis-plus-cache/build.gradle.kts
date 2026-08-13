/**
 * redis-plus-cache — 三级缓存模块
 *
 * 职责：L1 本地缓存（Caffeine）+ L2 Redis 缓存 + L3 数据库回源，
 *       通过 CacheLoadProtection SPI 实现回源保护，支持一致性策略 SPI
 */
dependencies {
    api(projects.redisPlusCore)

    // ThreeLevelCacheTemplate / CacheKeyResolver 的公开类型
    api(libs.bundles.cache.public.api)

    // 切面实现 + Caffeine 本地缓存实现 + Jackson 序列化实现（2.0 起不再保留无 TTL 的 Map 兜底）
    implementation(libs.bundles.cache.impl.support)

    // 可选：有 redis-plus-lock 时提供 DistributedCacheLoadProtection 实现
    compileOnly(projects.redisPlusLock)
}
