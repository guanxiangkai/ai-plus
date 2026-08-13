/**
 * redis-plus-lock — 分布式读写锁模块
 *
 * 职责：可重入分布式锁、读写锁、自动续期（WatchDog）、锁降级、AOP 注解接入
 */
dependencies {
    api(projects.redisPlusCore)

    implementation(libs.redisson)

    // 切面实现属于模块内细节
    compileOnly(libs.bundles.lock.impl.support)
}
