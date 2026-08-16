/**
 * redis-plus-datasource — 多 Redis 数据源切换模块
 *
 * 职责：多连接工厂管理、主从/读写路由、租户命名空间隔离、数据源上下文切换
 */
dependencies {
    api(projects.redisPlusCore)

    // MultiRedisConnectionFactory 的公开签名会暴露这些类型
    api(libs.bundles.datasource.public.api)

    // 公共 Builder 的可空参数契约使用 JSpecify，使用方编译时应能读取这些类型注解。
    compileOnlyApi(libs.jspecify)

    // 公共工厂构建器的 Lettuce 与连接池实现不进入方法签名，但运行时必须随模块提供。
    implementation(libs.bundles.datasource.client.runtime)

    // RedisDS 切面实现、生命周期销毁、@Order 与可选路由注入属于实现细节
    compileOnly(libs.bundles.datasource.impl.support)
}
