/**
 * jpa-plus-audit — 数据层审计模块
 *
 * <p>提供数据变更事件 {@code DataAuditEvent}、轻量快照 {@code AuditSnapshot}、
 * 以及同步/异步审计事件发布能力。</p>
 * <p><b>依赖边界：</b>模块公开暴露 core 中的拦截器/操作类型契约，以及 Spring 事件与 JPA 快照相关 API；
 * Boot 自动装配统一收敛在 jpa-plus-starter 模块中。</p>
 */
dependencies {
    api(projects.jpaPlusCore)               // DataAuditEvent / AuditInterceptor / SnapshotAuditInterceptor 暴露 core 类型
    api(libs.bundles.audit.public.api)           // ApplicationEventPublisher / EntityManager / ObjectMapper
}
