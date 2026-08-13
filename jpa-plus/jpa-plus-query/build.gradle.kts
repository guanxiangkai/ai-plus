/**
 * jpa-plus-query — 查询 DSL + Join + SQL 编译
 *
 * <p>依赖 jpa-plus-core，并对外暴露 Jakarta Persistence 与 Spring Data 分页抽象。</p>
 * <p>Hibernate 仅用于内部 Flush 脏检查实现，不进入公开 API。</p>
 * <p><b>依赖边界：</b>JPA 与 Spring Data 分页抽象属于公开 ABI；Hibernate 仅作为实现细节保留在内部。</p>
 */
dependencies {
    api(projects.jpaPlusCore)
    api(libs.bundles.query.public.api)           // QueryExecutor.page(..., Pageable) / EntityManager
    implementation(libs.hibernate.core)          // FlushStrategy 内部使用 Session.isDirty()
}

