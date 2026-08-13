package io.github.guanxiangkai.redis.plus.core.invoke;

/**
 * {@link InvocationContext} 工厂工具。
 *
 * <p>统一封装调用上下文下的 key 解析逻辑，
 * 避免各个切面重复书写“优先走 SPI、否则回退 SpEL”的样板代码。
 */
public final class InvocationContexts {

    private InvocationContexts() {
    }

    /**
     * 统一 AOP Key 解析：先走 SPI KeyResolver，无 SPI 时回退到内联 SpEL。
     */
    public static String resolveKey(InvocationContext context, String expression, InvocationKeyResolver keyResolver) {
        if (keyResolver != null) {
            return nullToEmpty(keyResolver.resolve(context, expression));
        }
        return resolveKey(context, expression);
    }

    /**
     * 统一 AOP Key 解析（无 SPI，仅 SpEL）。
     *
     * <p>等同于 {@link #resolveKey(InvocationContext, String, InvocationKeyResolver)
     * resolveKey(context, expression, null)}。
     */
    public static String resolveKey(InvocationContext context, String expression) {
        return nullToEmpty(InvocationContextSpelSupport.resolveToString(context, expression));
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
