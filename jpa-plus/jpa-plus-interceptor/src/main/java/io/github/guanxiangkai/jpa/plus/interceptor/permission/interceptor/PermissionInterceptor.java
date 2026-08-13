package io.github.guanxiangkai.jpa.plus.interceptor.permission.interceptor;

import io.github.guanxiangkai.jpa.plus.core.interceptor.Chain;
import io.github.guanxiangkai.jpa.plus.core.interceptor.DataInterceptor;
import io.github.guanxiangkai.jpa.plus.core.interceptor.Phase;
import io.github.guanxiangkai.jpa.plus.core.model.DataInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;
import io.github.guanxiangkai.jpa.plus.core.model.QueryInvocation;
import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.interceptor.permission.annotation.DataScope;
import io.github.guanxiangkai.jpa.plus.interceptor.permission.enums.DataScopeEnum;
import io.github.guanxiangkai.jpa.plus.interceptor.permission.enums.DataScopeType;
import io.github.guanxiangkai.jpa.plus.interceptor.permission.handler.DataScopeHandler;
import io.github.guanxiangkai.jpa.plus.query.ast.Condition;
import io.github.guanxiangkai.jpa.plus.query.ast.Conditions;
import io.github.guanxiangkai.jpa.plus.query.ast.Eq;
import io.github.guanxiangkai.jpa.plus.query.ast.In;
import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;
import io.github.guanxiangkai.jpa.plus.query.context.QueryRuntime;
import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据权限拦截器
 *
 * <p>在查询前根据实体类上的 {@link DataScope} 注解自动注入
 * 行级数据权限条件到 AST，实现多粒度的数据权限控制。
 * 支持内置 {@link DataScopeType} 和用户自定义 {@link DataScopeEnum} 实现。</p>
 *
 * @author guanxiangkai
 * @see DataScope
 * @see DataScopeEnum
 * @see DataScopeHandler
 * @since 1.0.0
 */
@Slf4j
public class PermissionInterceptor implements DataInterceptor {

    private static final ClassValue<DataScopeEnum> CUSTOM_SCOPE_ENUM_CACHE = new ClassValue<>() {
        @Override
        protected DataScopeEnum computeValue(Class<?> type) {
            return ReflectionUtils.instantiate(type.asSubclass(DataScopeEnum.class));
        }
    };

    private final DataScopeHandler handler;

    /**
     * 实体类 → @DataScope 注解缓存（含 absent 缓存，避免重复反射）
     */
    private final ClassValue<Optional<DataScope>> annotationCache = new ClassValue<>() {
        @Override
        protected Optional<DataScope> computeValue(Class<?> type) {
            return Optional.ofNullable(type.getAnnotation(DataScope.class));
        }
    };
    /**
     * Repository 方法 → @DataScope 注解缓存（方法级优先于类级）。
     * 使用 Optional 包装以允许缓存 absent 结果（ConcurrentHashMap 不允许 null 值）。
     */
    private final Map<Method, Optional<DataScope>> methodAnnotationCache = new ConcurrentHashMap<>();

    public PermissionInterceptor(DataScopeHandler handler) {
        this.handler = Objects.requireNonNull(handler, "DataScopeHandler must not be null");
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public Phase phase() {
        return Phase.BEFORE;
    }

    @Override
    public boolean supports(OperationType type) {
        return type == OperationType.QUERY;
    }

    @Override
    public Object intercept(DataInvocation invocation, Chain chain) throws Throwable {
        if (invocation instanceof QueryInvocation qi && qi.queryContext() instanceof QueryContext ctx) {
            Class<?> entityClass = ctx.metadata().root().entityClass();

            DataScope scope = qi.repositoryMethod() != null
                    ? resolveDataScopeFromMethod(qi.repositoryMethod(), entityClass)
                    : resolveDataScope(entityClass);

            if (scope != null) {
                DataScopeEnum scopeEnum = resolveScopeEnum(scope);
                if (!scopeEnum.skipFilter()) {
                    Condition permission = buildCondition(scope, scopeEnum, ctx);
                    if (permission != null) {
                        Condition combined = Conditions.and(ctx.runtime().where(), permission);
                        QueryRuntime newRuntime = ctx.runtime().withWhere(combined);
                        invocation = qi.withQueryContext(ctx.withRuntime(newRuntime));

                        if (log.isDebugEnabled()) {
                            log.debug("DataScope: entity={}, scope={}, method={}",
                                    entityClass.getSimpleName(), scopeEnum.scopeName(),
                                    qi.repositoryMethod() != null
                                            ? qi.repositoryMethod().getName() : "class-level");
                        }
                    }
                }
            }
        }
        return chain.proceed(invocation);
    }

    /**
     * 方法级 @DataScope 解析：方法注解 → 实体类注解
     * 使用 Optional 缓存以避免对无注解方法的重复反射扫描（ConcurrentHashMap 不缓存 null）。
     */
    private DataScope resolveDataScopeFromMethod(Method method, Class<?> entityClass) {
        DataScope methodScope = methodAnnotationCache
                .computeIfAbsent(method, m -> Optional.ofNullable(m.getAnnotation(DataScope.class)))
                .orElse(null);
        return methodScope != null ? methodScope : resolveDataScope(entityClass);
    }

    private DataScope resolveDataScope(Class<?> entityClass) {
        return annotationCache.get(entityClass).orElse(null);
    }

    /**
     * 解析 DataScopeEnum：customType 优先于 type
     */
    private DataScopeEnum resolveScopeEnum(DataScope scope) {
        Class<? extends DataScopeEnum> customClass = scope.customType();
        if (customClass != DataScopeEnum.class) {
            return CUSTOM_SCOPE_ENUM_CACHE.get(customClass);
        }
        return scope.type();
    }

    /**
     * 根据 scope 类型构建对应的权限条件
     */
    private Condition buildCondition(DataScope scope, DataScopeEnum scopeEnum, QueryContext ctx) {
        // 内置类型走 switch 分派
        if (scopeEnum instanceof DataScopeType builtIn) {
            return switch (builtIn) {
                case ALL -> null;

                case DEPT -> {
                    Object deptId = handler.getCurrentDeptId();
                    // P0: null/missing context must deny access, not grant it.
                    if (deptId == null) {
                        throw new SecurityException(
                                "[jpa-plus] DataScope DEPT: cannot determine current dept ID. " +
                                        "Ensure DataScopeHandler.getCurrentDeptId() returns a non-null value.");
                    }
                    yield new Eq(ColumnMeta.of(ctx.metadata().root(), scope.deptColumn(), deptId.getClass()), deptId);
                }

                case DEPT_AND_CHILD -> {
                    Collection<?> deptIds = handler.getDeptAndChildIds();
                    // P0: empty/null dept set means zero accessible rows, not all rows.
                    if (deptIds == null || deptIds.isEmpty()) {
                        throw new SecurityException(
                                "[jpa-plus] DataScope DEPT_AND_CHILD: cannot determine accessible dept IDs. " +
                                        "Ensure DataScopeHandler.getDeptAndChildIds() returns a non-null, non-empty collection.");
                    }
                    yield new In(ColumnMeta.of(ctx.metadata().root(), scope.deptColumn(), Long.class), deptIds);
                }

                case SELF -> {
                    Object userId = handler.getCurrentUserId();
                    // P0: null user must deny access, not grant it.
                    if (userId == null) {
                        throw new SecurityException(
                                "[jpa-plus] DataScope SELF: cannot determine current user ID. " +
                                        "Ensure DataScopeHandler.getCurrentUserId() returns a non-null value.");
                    }
                    yield new Eq(ColumnMeta.of(ctx.metadata().root(), scope.userColumn(), userId.getClass()), userId);
                }

                case CUSTOM -> handler.customCondition(ctx.metadata().root().entityClass());
            };
        }

        // 自定义类型：统一走 handler.customCondition()
        return handler.customCondition(ctx.metadata().root().entityClass());
    }
}
