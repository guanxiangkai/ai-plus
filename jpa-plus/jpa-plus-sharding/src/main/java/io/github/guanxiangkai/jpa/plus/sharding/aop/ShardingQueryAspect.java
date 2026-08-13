package io.github.guanxiangkai.jpa.plus.sharding.aop;

import io.github.guanxiangkai.jpa.plus.datasource.context.JpaPlusContext;
import io.github.guanxiangkai.jpa.plus.sharding.annotation.ShardingQuery;
import io.github.guanxiangkai.jpa.plus.sharding.model.ShardingContext;
import io.github.guanxiangkai.jpa.plus.sharding.model.ShardingTarget;
import io.github.guanxiangkai.jpa.plus.sharding.router.ShardingRouter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code @ShardingQuery} SpEL 参数绑定 AOP 切面
 *
 * <p>拦截标注了 {@link ShardingQuery} 的 Repository 方法，使用 Spring SpEL
 * 解析 {@code keyExpression} 从方法参数中提取分片键值，然后路由到目标分片数据源执行查询。</p>
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>从 {@link MethodSignature} 提取方法和参数信息</li>
 *   <li>按优先级绑定参数名：{@code @Param("name") > 反射参数名 > p0/p1...}</li>
 *   <li>使用 SpEL 计算 {@code keyExpression}，得到分片键值</li>
 *   <li>调用 {@link ShardingRouter#routeByKey} 计算目标库表</li>
 *   <li>在 {@link ShardingContext} + {@link JpaPlusContext} 双重作用域中执行原方法</li>
 *   <li>对返回值进行 null 安全适配（Optional / Page&lt;T&gt; / List）</li>
 * </ol>
 *
 * <h3>返回类型适配</h3>
 * <p>当路由后原方法返回 {@code null} 时，自动适配为安全的空值：</p>
 * <ul>
 *   <li>{@code Optional<T>} → {@link Optional#empty()}</li>
 *   <li>{@code Page<T>}（Spring Data）→ 空 {@code Page}（含原 {@code Pageable} 参数）</li>
 *   <li>{@code List<T>} / {@code Collection<T>} → {@link List#of()}</li>
 * </ul>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class ShardingQueryAspect {

    private final ShardingRouter router;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();
    private final Map<Method, String[]> paramNamesCache = new ConcurrentHashMap<>();

    public ShardingQueryAspect(ShardingRouter router) {
        this.router = router;
    }

    /**
     * 对返回值进行 null 安全适配，保证方法签名声明的返回类型不会意外返回 {@code null}。
     */
    private static Object adaptReturnType(Object result, Method method, Object[] args) {
        if (result != null) return result; // 最常见路径，快速返回

        Class<?> returnType = method.getReturnType();

        // java.util.Optional<T>
        if (Optional.class.isAssignableFrom(returnType)) {
            return Optional.empty();
        }

        if (Page.class.isAssignableFrom(returnType)) {
            return createEmptyPage(args);
        }

        // java.util.List / java.util.Collection
        if (java.util.List.class.isAssignableFrom(returnType)
                || java.util.Collection.class.isAssignableFrom(returnType)) {
            return List.of();
        }

        return null;
    }

    // ─── 返回类型适配 ─────────────────────────────────────────────────────────

    private static Page<?> createEmptyPage(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Pageable pageable) {
                return new PageImpl<>(List.of(), pageable, 0L);
            }
        }
        return new PageImpl<>(List.of());
    }

    private static String extractAnnotatedName(Parameter param) {
        for (Annotation ann : param.getAnnotations()) {
            String simpleName = ann.annotationType().getSimpleName();
            if ("Param".equals(simpleName) || "RequestParam".equals(simpleName)) {
                try {
                    String value = (String) ann.annotationType().getMethod("value").invoke(ann);
                    if (value != null && !value.isBlank()) return value;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    // ─── SpEL 求值 ────────────────────────────────────────────────────────────

    @Around("@annotation(shardingQuery)")
    public Object around(ProceedingJoinPoint pjp, ShardingQuery shardingQuery) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Object[] args = pjp.getArgs();

        // ① 计算分片键
        Object shardingKey = evaluateKey(method, args, shardingQuery.keyExpression());
        if (shardingKey == null) {
            throw new IllegalArgumentException(
                    "[jpa-plus] @ShardingQuery keyExpression '" + shardingQuery.keyExpression() +
                            "' evaluated to null on method: " + method.getDeclaringClass().getSimpleName() +
                            "#" + method.getName() + ". Sharding key must not be null.");
        }

        // ② 路由到目标分片
        ShardingTarget target = router.routeByKey(shardingQuery.logicTable(), shardingKey);
        log.debug("[jpa-plus-sharding] @ShardingQuery '{}' → db='{}', table='{}'",
                method.getName(), target.db(), target.table());

        // ③ 在分片上下文 + 数据源上下文中执行原方法
        JpaPlusContext.ThrowableCallable<Object> innerTask = pjp::proceed;
        ShardingContext.ThrowableCallable<Object> outerTask =
                () -> JpaPlusContext.withDS(target.db(), innerTask);
        Object result = ShardingContext.withTarget(target, outerTask);

        // ④ 返回类型 null 安全适配
        return adaptReturnType(result, method, args);
    }

    private Object evaluateKey(Method method, Object[] args, String expression) {
        EvaluationContext context = buildContext(method, args);
        Expression expr = expressionCache.computeIfAbsent(expression, parser::parseExpression);
        return expr.getValue(context);
    }

    // ─── 参数名解析 ───────────────────────────────────────────────────────────

    private EvaluationContext buildContext(Method method, Object[] args) {
        // P1-10: Use SimpleEvaluationContext to prevent SpEL injection (no T(...) type access).
        SimpleEvaluationContext ctx = SimpleEvaluationContext.forReadOnlyDataBinding()
                .withInstanceMethods()
                .build();
        String[] names = resolveParamNames(method);
        for (int i = 0; i < args.length && i < names.length; i++) {
            ctx.setVariable(names[i], args[i]);
        }
        return ctx;
    }

    private String[] resolveParamNames(Method method) {
        return paramNamesCache.computeIfAbsent(method, m -> {
            Parameter[] params = m.getParameters();
            String[] names = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                String name = extractAnnotatedName(params[i]);
                if (name == null || name.isBlank()) {
                    name = params[i].isNamePresent() ? params[i].getName() : "p" + i;
                }
                names[i] = name;
            }
            return names;
        });
    }

}
