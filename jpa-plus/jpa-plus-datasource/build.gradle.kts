/**
 * jpa-plus-datasource — 多数据源路由
 *
 * <p>提供 @DS 注解、动态路由数据源、运行时数据源注册，
 * 基于 ScopedValue 实现线程安全的数据源切换（虚拟线程友好）</p>
 * <p><b>依赖边界：</b>模块公开暴露数据源事件、JDBC 路由与健康检查相关 API；AOP 切面运行时仅保留在内部实现层。</p>
 */
dependencies {
    api(projects.jpaPlusCore)                       // @DS / DynamicRoutingDataSource 等公开能力建立在 core 契约之上
    api(libs.bundles.datasource.public.api)            // ApplicationEvent / JDBC 路由 / HealthIndicator
    compileOnly(libs.jackson.annotations)              // 补齐 spring-boot-health classfile 中的 Jackson 2 注解元数据，避免编译期 warning
    implementation(libs.bundles.datasource.aop.runtime) // DSAspect 内部切面实现
}
