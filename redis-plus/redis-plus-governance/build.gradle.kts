/**
 * redis-plus-governance — 治理与运维能力模块
 *
 * 职责：Micrometer 指标实现、Spring Boot Actuator 健康检查、
 *       分片路由辅助、高可用抽象（Sentinel / Cluster）
 */
dependencies {
    api(projects.redisPlusCore)

    // MetricsTagContributor / RedisPlusHealthContributor 的公开签名会暴露这些类型
    api(libs.bundles.governance.public.api)
    compileOnly(libs.jackson.annotations) // 补齐 spring-boot-health classfile 中的 Jackson 2 注解元数据，避免编译期 warning
}
