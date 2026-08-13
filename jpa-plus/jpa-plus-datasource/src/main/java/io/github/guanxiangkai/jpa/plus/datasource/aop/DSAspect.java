package io.github.guanxiangkai.jpa.plus.datasource.aop;

import io.github.guanxiangkai.jpa.plus.datasource.annotation.DS;
import io.github.guanxiangkai.jpa.plus.datasource.context.JpaPlusContext;
import io.github.guanxiangkai.jpa.plus.datasource.enums.DsName;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;

/**
 * 数据源切换切面
 *
 * <p>拦截 {@link DS @DS} 注解，在方法执行前通过 {@link JpaPlusContext}
 * 创建 ScopedValue 作用域，方法结束后 <b>自动恢复到上层数据源（默认 master）</b>。</p>
 *
 * <h3>核心保证</h3>
 * <ul>
 *   <li>标注 {@code @DS("slave")} → 方法内所有 SQL 走 slave</li>
 *   <li>方法执行完毕（正常或异常）→ 自动退出 ScopedValue 作用域 → 恢复为 master</li>
 *   <li>嵌套调用：{@code @DS("slave")} 内部调用 {@code @DS("analytics")} → analytics 生效 → 返回后恢复 slave → 最终恢复 master</li>
 *   <li>不加 {@code @DS} 的方法 → 不经过此切面 → 始终走默认 master</li>
 * </ul>
 *
 * <p><b>为什么不会"粘连到从库"？</b><br/>
 * {@link ScopedValue} 是块作用域的（block-scoped），
 * 与 ThreadLocal 不同，它不需要手动 remove()，
 * 离开 {@code ScopedValue.where().call()} 块后自动回到外层绑定值（或无绑定 → master）。</p>
 *
 * <h3>事务安全</h3>
 * <p>切面使用 {@code @Order(Ordered.HIGHEST_PRECEDENCE)}，保证在 {@code @Transactional} 事务拦截器
 * <b>之前</b>执行，使得 ScopedValue 路由 key 在事务开启（获取连接）前就已设置。</p>
 *
 * <p>在活跃事务内切换到 <b>不同</b> 数据源时，切面会抛出异常以防止数据一致性问题。
 * 如需跨库操作，建议使用 {@code @Transactional(propagation = REQUIRES_NEW)} 开启新事务，
 * 或通过 {@link io.github.guanxiangkai.jpa.plus.datasource.spi.DataSourcePostProcessor}
 * 集成 Seata 等分布式事务框架。</p>
 *
 * <h3>优先级</h3>
 * <p>方法级 {@code @DS} 优先于类级 {@code @DS}。</p>
 *
 * @author guanxiangkai
 * @see DS
 * @see JpaPlusContext
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DSAspect {

    private final BeanFactory beanFactory;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final TemplateParserContext templateParserContext = new TemplateParserContext();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public DSAspect() {
        this(null);
    }

    public DSAspect(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * 拦截所有标注 {@code @DS} 的方法和类中的方法
     */
    @Around("@annotation(io.github.guanxiangkai.jpa.plus.datasource.annotation.DS) || " +
            "@within(io.github.guanxiangkai.jpa.plus.datasource.annotation.DS)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        String dsName = resolveDS(point);

        // ── 事务安全检测 ──
        checkTransactionConflict(dsName, point);

        if (log.isDebugEnabled()) {
            log.debug("@DS switch: {} → {}", point.getSignature().toShortString(), dsName);
        }

        // ScopedValue.where().call() 创建一个块作用域：
        // - 进入块：CURRENT_DS 绑定为 dsName
        // - 退出块（正常/异常）：CURRENT_DS 自动恢复为外层绑定值（无外层则回到 unbound → master）
        return JpaPlusContext.withDS(dsName, (JpaPlusContext.ThrowableCallable<Object>) point::proceed);
    }

    /**
     * 检测事务冲突 —— 在活跃事务内不允许切换到不同的数据源
     *
     * <p>Spring 的 {@code @Transactional} 事务绑定在特定数据源的连接上，
     * 如果在事务中途切换数据源，新的 SQL 会走不同的连接，
     * 导致事务语义被破坏（部分提交 / 部分回滚）。</p>
     *
     * @param targetDS 即将切换的目标数据源名称
     * @param point    切入点信息（用于错误日志）
     */
    private void checkTransactionConflict(String targetDS, ProceedingJoinPoint point) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return; // 没有活跃事务，安全切换
        }

        String currentDS = JpaPlusContext.currentDS();
        if (currentDS.equals(targetDS)) {
            return; // 相同数据源无需担心
        }

        throw new IllegalStateException(
                "Cannot switch datasource from '" + currentDS + "' to '" + targetDS +
                        "' inside an active transaction at [" + point.getSignature().toShortString() + "]. " +
                        "This would break transaction consistency. Solutions: " +
                        "(1) Use @Transactional(propagation = Propagation.REQUIRES_NEW) to start a new transaction; " +
                        "(2) Move the @DS method call outside the transaction; " +
                        "(3) Integrate distributed transaction (e.g. Seata) via DataSourcePostProcessor."
        );
    }

    /**
     * 解析数据源名称（方法级优先于类级）
     */
    private String resolveDS(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();

        // ① 方法级 @DS（最高优先级）
        DS methodDS = method.getAnnotation(DS.class);
        if (methodDS != null) {
            return resolveSpelIfNecessary(methodDS.value(), method, point.getArgs());
        }

        // ② 类级 @DS
        DS classDS = point.getTarget().getClass().getAnnotation(DS.class);
        if (classDS != null) {
            return resolveSpelIfNecessary(classDS.value(), method, point.getArgs());
        }

        // ③ 无注解 → master（理论上不会走到这里，因为切点已限定）
        return DsName.MASTER;
    }

    /**
     * 支持 @DS("#{...}") SpEL 动态路由
     *
     * <p><b>安全说明：</b>使用 {@link SimpleEvaluationContext} 替代 {@code StandardEvaluationContext}
     * 以防止 SpEL 注入攻击（禁止 {@code T(...)} 类型访问和任意方法调用）。
     * 仅支持变量引用（{@code #p0}、{@code #paramName}）和实例方法调用。
     * Bean 引用（{@code @beanName}）不再受支持；如需动态路由请通过变量传值。</p>
     */
    private String resolveSpelIfNecessary(String dsValue, Method method, Object[] args) {
        if (dsValue == null || dsValue.isBlank()) {
            return DsName.MASTER;
        }
        if (!dsValue.startsWith("#{")) {
            return dsValue;
        }

        // P1-03: Use SimpleEvaluationContext to prevent SpEL injection (no type access, no bean refs).
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding()
                .withInstanceMethods()
                .build();
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        String resolved = expressionParser.parseExpression(dsValue, templateParserContext)
                .getValue(context, String.class);
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalArgumentException("SpEL 路由表达式解析结果为空: " + dsValue);
        }
        return resolved.trim();
    }
}
