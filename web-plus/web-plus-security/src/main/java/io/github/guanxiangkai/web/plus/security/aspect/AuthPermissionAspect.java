package io.github.guanxiangkai.web.plus.security.aspect;

import io.github.guanxiangkai.web.plus.security.annotation.RequiresAnyRole;
import io.github.guanxiangkai.web.plus.security.annotation.RequiresLogin;
import io.github.guanxiangkai.web.plus.security.annotation.RequiresPermission;
import io.github.guanxiangkai.web.plus.security.annotation.RequiresRole;
import io.github.guanxiangkai.web.plus.security.spi.PermissionResolver;
import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import io.github.guanxiangkai.web.plus.core.util.SafeSpelTemplateEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

/**
 * 权限校验 AOP 切面（同步 + 响应式双模式）
 * <p>
 * 拦截标注了以下注解的方法，在执行前校验当前用户是否满足权限要求：
 * <ul>
 *   <li>{@link RequiresLogin} —— 需要已登录</li>
 *   <li>{@link RequiresPermission} —— 需要特定权限，权限值支持 SpEL 模板（{@code #{expr}}）</li>
 *   <li>{@link RequiresRole} —— 需要特定角色（全部满足）</li>
 *   <li>{@link RequiresAnyRole} —— 需要任一角色（满足一个即可）</li>
 * </ul>
 * 注解可标注在方法上，也可标注在类上（类上的注解对所有方法生效）。
 * 方法注解优先于类注解。
 * </p>
 *
 * <h3>响应式支持</h3>
 * <p>
 * 当方法返回 {@link Mono} 或 {@link Flux} 时，切面通过
 * {@link CurrentUserProvider#getCurrentUserMono()} 从 Reactor Context 读取当前用户，
 * 将权限检查延迟到订阅时执行，彻底避免对 ThreadLocal 的隐式依赖。
 * 同步方法仍走 ThreadLocal 快速路径。
 * </p>
 *
 * <h3>SpEL 动态权限示例</h3>
 * <pre>
 * &#64;RequiresPermission("#{getPermissionPrefix() + ':list'}")
 * public Mono&lt;ApiResponse&lt;PageResponse&lt;LV&gt;&gt;&gt; list(Q query) { ... }
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class AuthPermissionAspect {

    private final PermissionResolver permissionResolver;
    private final CurrentUserProvider currentUserProvider;

    @Around("@within(io.github.guanxiangkai.web.plus.security.annotation.RequiresLogin)" +
            " || @annotation(io.github.guanxiangkai.web.plus.security.annotation.RequiresLogin)" +
            " || @within(io.github.guanxiangkai.web.plus.security.annotation.RequiresPermission)" +
            " || @annotation(io.github.guanxiangkai.web.plus.security.annotation.RequiresPermission)" +
            " || @within(io.github.guanxiangkai.web.plus.security.annotation.RequiresRole)" +
            " || @annotation(io.github.guanxiangkai.web.plus.security.annotation.RequiresRole)" +
            " || @within(io.github.guanxiangkai.web.plus.security.annotation.RequiresAnyRole)" +
            " || @annotation(io.github.guanxiangkai.web.plus.security.annotation.RequiresAnyRole)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> returnType = method.getReturnType();

        // ── 响应式路径：从 Reactor Context 读取用户，延迟到订阅时校验 ──
        if (Mono.class.isAssignableFrom(returnType)) {
            @SuppressWarnings("unchecked")
            Mono<Object> downstream = (Mono<Object>) joinPoint.proceed();
            return currentUserProvider.getCurrentUserMono()
                    .flatMap(userOpt -> {
                        try {
                            enforce(method, joinPoint.getTarget().getClass(),
                                    userOpt.orElse(null), joinPoint);
                        } catch (RuntimeException e) {
                            return Mono.error(e);
                        }
                        return downstream;
                    });
        }
        if (Flux.class.isAssignableFrom(returnType)) {
            @SuppressWarnings("unchecked")
            Flux<Object> downstream = (Flux<Object>) joinPoint.proceed();
            return currentUserProvider.getCurrentUserMono()
                    .flatMapMany(userOpt -> {
                        try {
                            enforce(method, joinPoint.getTarget().getClass(),
                                    userOpt.orElse(null), joinPoint);
                        } catch (RuntimeException e) {
                            return Flux.error(e);
                        }
                        return downstream;
                    });
        }

        // ── 同步路径：ThreadLocal（Micrometer Context Propagation 已还原） ──
        CurrentUser user = CurrentUserHolder.get();
        enforce(method, joinPoint.getTarget().getClass(), user, joinPoint);
        return joinPoint.proceed();
    }

    // ── 权限执行逻辑（同步/响应式共用） ─────────────────────────────────

    private void enforce(Method method, Class<?> clazz, CurrentUser user,
                         ProceedingJoinPoint joinPoint) {
        // ── @RequiresLogin ──
        RequiresLogin requiresLogin = findAnnotation(method, clazz, RequiresLogin.class);
        if (requiresLogin != null) {
            requireLogin(user);
        }
        if (isSuperAdmin(user)) {
            return;
        }

        // ── @RequiresRole ──
        RequiresRole requiresRole = findAnnotation(method, clazz, RequiresRole.class);
        if (requiresRole != null) {
            requireLogin(user);
            boolean ok = requiresRole.any()
                    ? permissionResolver.hasAnyRole(user, requiresRole.value())
                    : permissionResolver.hasAllRoles(user, requiresRole.value());
            if (!ok) throw permissionDenied("角色权限不足");
        }

        // ── @RequiresAnyRole ──
        RequiresAnyRole requiresAnyRole = findAnnotation(method, clazz, RequiresAnyRole.class);
        if (requiresAnyRole != null) {
            requireLogin(user);
            if (!permissionResolver.hasAnyRole(user, requiresAnyRole.value())) {
                throw permissionDenied(requiresAnyRole.message());
            }
        }

        // ── @RequiresPermission（支持 SpEL 模板） ──
        RequiresPermission requiresPermission = findAnnotation(method, clazz, RequiresPermission.class);
        if (requiresPermission != null) {
            requireLogin(user);
            boolean ok;
            if (requiresPermission.any()) {
                ok = false;
                for (String p : requiresPermission.value()) {
                    String resolved = resolvePermission(p, joinPoint);
                    if (permissionResolver.hasPermission(user, resolved)) {
                        ok = true;
                        break;
                    }
                }
            } else {
                ok = true;
                for (String p : requiresPermission.value()) {
                    String resolved = resolvePermission(p, joinPoint);
                    if (!permissionResolver.hasPermission(user, resolved)) {
                        ok = false;
                        break;
                    }
                }
            }
            if (!ok) throw permissionDenied("功能权限不足");
        }
    }

    // ── 私有工具 ────────────────────────────────────────────────

    /**
     * 解析权限值中的 SpEL 模板表达式（以 Controller 实例为 root object）。
     * <p>
     * 若值不包含 {@code #{} 则原样返回，否则按 SpEL 模板求值：
     * <pre>
     *   "#{getPermissionPrefix() + ':list'}"  →  "sys:user:list"
     * </pre>
     * </p>
     */
    private String resolvePermission(String expr, ProceedingJoinPoint jp) {
        if (expr == null || !expr.contains("#{")) {
            return expr;
        }
        try {
            return SafeSpelTemplateEvaluator.evaluate(expr, jp.getTarget());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[web-plus] 权限 SpEL 解析失败，请检查注解配置: expr=" + expr, e);
        }
    }

    private <A extends java.lang.annotation.Annotation> A findAnnotation(
            Method method, Class<?> clazz, Class<A> annotationType) {
        A ann = AnnotationUtils.findAnnotation(method, annotationType);
        if (ann == null) {
            ann = AnnotationUtils.findAnnotation(clazz, annotationType);
        }
        return ann;
    }

    private void requireLogin(CurrentUser user) {
        if (user == null) {
            throw new io.github.guanxiangkai.web.plus.error.exception.AuthException(
                    io.github.guanxiangkai.web.plus.error.enums.WebErrorCode.UNAUTHORIZED);
        }
    }

    private boolean isSuperAdmin(CurrentUser user) {
        return user != null && Boolean.TRUE.equals(user.superAdmin());
    }

    private io.github.guanxiangkai.web.plus.error.exception.PermissionDeniedException permissionDenied(String msg) {
        return new io.github.guanxiangkai.web.plus.error.exception.PermissionDeniedException(msg);
    }
}
