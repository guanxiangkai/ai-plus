/**
 * jpa-plus-starter — Spring Boot 自动装配
 *
 * <p>聚合所有模块，提供开箱即用的 Spring Boot Starter。
 * 通过 {@code @AutoConfiguration} 自动注册所有核心 Bean。</p>
 *
 * <p><b>依赖边界：</b>starter 既承担自动装配职责，也作为业务项目的单依赖编译入口，
 * 因此功能模块依赖保留 {@code api} 暴露，确保使用方在仅引入 starter 时即可直接访问
 * Repository、注解、事件与 SPI 类型；Boot 装配与可选增强继续收敛在内部实现层。</p>
 */
plugins {
    alias(libs.plugins.springboot) apply false
}

dependencies {
    // ═══════════ Spring Boot BOM（暴露给最终用户的版本管理） ═══════════
    api(platform(libs.spring.boot.dependencies))

    // ═══════════ 核心模块 ═══════════
    // jpa-plus-core 由 query / field / audit / datasource / sharding 传递暴露，无需重复直连
    api(projects.jpaPlusQuery)

    // ═══════════ 治理模块（合并后） ═══════════
    api(projects.jpaPlusField)          // 字段治理：加密/脱敏/字典/敏感词/乐观锁
    api(projects.jpaPlusInterceptor)    // 数据拦截：逻辑删除/自动排序/数据权限/多租户

    // ═══════════ 独立治理模块 ═══════════
    api(projects.jpaPlusAudit)          // 数据层审计事件 + 快照能力
    api(projects.jpaPlusDatasource)     // 多数据源路由（ScopedValue）
    api(projects.jpaPlusSharding)       // 分库分表路由（Hash-Mod，SPI 可替换算法）

    // ═══════════ Starter 公开 ABI ═══════════
    api(libs.bundles.starter.public.api)                   // JpaRepository / Pageable / EntityManager / ApplicationEventPublisher

    // ═══════════ Starter 内部基础设施 ═══════════
    implementation(libs.bundles.starter.internal)          // JPA、Validation、自动配置、JDBC、Binder 与 Bean 装配
    annotationProcessor(platform(libs.spring.boot.dependencies))
    annotationProcessor(libs.bundles.starter.configuration.processor)

    // 仅用于验证消费者通过自动装配获得 Repository 生命周期，不随 starter 发布。
    testRuntimeOnly(libs.h2)

    // ═══════════ 可选增强（compileOnly：不引入则零开销） ═══════════
    compileOnly(libs.bundles.starter.optional)              // Micrometer / datasource-proxy / Druid
}
