/**
 * redis-plus-ratelimit — Redis 限流能力模块
 *
 * 职责：固定窗口 / 滑动窗口 / 令牌桶 / 漏桶限流，
 *       限流注解、切面与算法 SPI
 */
dependencies {
    api(projects.redisPlusCore)

    // 限流公开类型会暴露 RedisTemplate 相关签名
    api(libs.bundles.ratelimit.public.api)

    // Bucket4j 是本地 TOKEN_BUCKET 后端，Redisson 是分布式 TOKEN_BUCKET 后端
    implementation(libs.bundles.ratelimit.strategy.backends)

    // 切面实现依赖 AspectJ
    compileOnly(libs.aspectjweaver)
}
