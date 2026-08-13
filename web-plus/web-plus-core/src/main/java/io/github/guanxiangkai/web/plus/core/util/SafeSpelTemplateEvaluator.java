package io.github.guanxiangkai.web.plus.core.util;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * 受限的 SpEL 模板求值器。
 * <p>
 * 仅允许读取 root object 的属性与实例方法，显式禁止类型引用、构造器与 Bean 引用，
 * 用于注解模板等需要轻量表达式但不应开放反射能力的场景。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class SafeSpelTemplateEvaluator {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final TemplateParserContext TEMPLATE_CONTEXT = new TemplateParserContext();
    private static final SimpleEvaluationContext EVALUATION_CONTEXT = SimpleEvaluationContext
            .forReadOnlyDataBinding()
            .withInstanceMethods()
            .build();

    private SafeSpelTemplateEvaluator() {
    }

    public static String evaluate(String expr, Object rootObject) {
        if (expr == null || !expr.contains("#{")) {
            return expr;
        }
        return PARSER.parseExpression(expr, TEMPLATE_CONTEXT)
                .getValue(EVALUATION_CONTEXT, rootObject, String.class);
    }
}
