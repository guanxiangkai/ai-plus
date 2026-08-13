package io.github.guanxiangkai.web.plus.core.util;

import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.SpelEvaluationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeSpelTemplateEvaluatorTest {

    @Test
    void resolvesInstanceMethodsWithoutOpeningTypeAccess() {
        assertThat(SafeSpelTemplateEvaluator.evaluate(
                "#{getPermissionPrefix() + ':list'}",
                new DemoController()
        )).isEqualTo("sys:user:list");
    }

    @Test
    void rejectsTypeReferences() {
        assertThatThrownBy(() -> SafeSpelTemplateEvaluator.evaluate(
                "#{T(java.lang.Runtime).getRuntime().exec('id')}",
                new DemoController()
        )).isInstanceOf(SpelEvaluationException.class);
    }

    private static final class DemoController {

        public String getPermissionPrefix() {
            return "sys:user";
        }
    }
}
