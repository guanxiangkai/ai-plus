/**
 * jpa-plus-interceptor — 数据拦截（合并模块）
 *
 * <p>聚合所有 DataInterceptor 实现：
 * <ul>
 *   <li>逻辑删除（@LogicDelete → LogicDeleteInterceptor + LogicDeleteFieldHandler）</li>
 *   <li>自动排序（@AutoOrderBy → AutoOrderByInterceptor）</li>
 *   <li>数据权限（PermissionInterceptor）</li>
 *   <li>多租户隔离（TenantInterceptor）</li>
 * </ul>
 * </p>
 * <p><b>依赖边界：</b>模块公开暴露 core 拦截器契约，以及 query AST/上下文相关 SPI；因此 query 仍需保留为公开依赖。</p>
 */
dependencies {
    api(projects.jpaPlusCore)
    api(projects.jpaPlusQuery)          // DataScopeHandler 公共 SPI 返回 query.ast.Condition
}

