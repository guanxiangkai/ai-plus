package io.github.guanxiangkai.jpa.plus.core.interceptor;

import io.github.guanxiangkai.jpa.plus.core.model.DataInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;
import io.github.guanxiangkai.jpa.plus.core.model.QueryInvocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterceptorChainTest {

    @Test
    void emptyChain_passesThrough() throws Throwable {
        InterceptorChain chain = new InterceptorChain(List.of());
        var inv = new QueryInvocation(Object.class, null);
        Object result = chain.proceed(inv, i -> "result");
        assertThat(result).isEqualTo("result");
    }

    @Test
    void beforeInterceptor_modifiesInvocation() throws Throwable {
        List<String> log = new ArrayList<>();
        DataInterceptor interceptor = new DataInterceptor() {
            @Override
            public int order() {
                return 1;
            }

            @Override
            public Phase phase() {
                return Phase.BEFORE;
            }

            @Override
            public boolean supports(OperationType type) {
                return true;
            }

            @Override
            public Object intercept(DataInvocation inv, Chain chain) throws Throwable {
                log.add("before");
                return chain.proceed(inv);
            }
        };
        InterceptorChain ic = new InterceptorChain(List.of(interceptor));
        ic.proceed(new QueryInvocation(Object.class, null), i -> "ok");
        assertThat(log).containsExactly("before");
    }

    @Test
    void afterInterceptor_seesResult() throws Throwable {
        List<Object> seen = new ArrayList<>();
        DataInterceptor interceptor = new DataInterceptor() {
            @Override
            public int order() {
                return 1;
            }

            @Override
            public Phase phase() {
                return Phase.AFTER;
            }

            @Override
            public boolean supports(OperationType type) {
                return true;
            }

            @Override
            public Object intercept(DataInvocation inv, Chain chain) throws Throwable {
                Object res = chain.proceed(inv);
                seen.add(res);
                return res;
            }
        };
        InterceptorChain ic = new InterceptorChain(List.of(interceptor));
        ic.proceed(new QueryInvocation(Object.class, null), i -> "after-result");
        assertThat(seen).containsExactly("after-result");
    }

    @Test
    void multipleInterceptors_orderedCorrectly() throws Throwable {
        List<String> order = new ArrayList<>();
        DataInterceptor first = new DataInterceptor() {
            @Override
            public int order() {
                return 1;
            }

            @Override
            public Phase phase() {
                return Phase.BEFORE;
            }

            @Override
            public boolean supports(OperationType type) {
                return true;
            }

            @Override
            public Object intercept(DataInvocation inv, Chain chain) throws Throwable {
                order.add("first");
                return chain.proceed(inv);
            }
        };
        DataInterceptor second = new DataInterceptor() {
            @Override
            public int order() {
                return 2;
            }

            @Override
            public Phase phase() {
                return Phase.BEFORE;
            }

            @Override
            public boolean supports(OperationType type) {
                return true;
            }

            @Override
            public Object intercept(DataInvocation inv, Chain chain) throws Throwable {
                order.add("second");
                return chain.proceed(inv);
            }
        };
        InterceptorChain ic = new InterceptorChain(List.of(second, first)); // reverse order
        ic.proceed(new QueryInvocation(Object.class, null), i -> "x");
        assertThat(order).containsExactly("first", "second");
    }

    @Test
    void beforeInterceptor_canWrapDownstreamExecution() throws Throwable {
        List<String> events = new ArrayList<>();

        DataInterceptor outer = new DataInterceptor() {
            @Override
            public int order() {
                return 1;
            }

            @Override
            public Phase phase() {
                return Phase.BEFORE;
            }

            @Override
            public boolean supports(OperationType type) {
                return true;
            }

            @Override
            public Object intercept(DataInvocation inv, Chain chain) throws Throwable {
                events.add("outer-before");
                Object result = chain.proceed(inv);
                events.add("outer-after");
                return result;
            }
        };

        DataInterceptor inner = new DataInterceptor() {
            @Override
            public int order() {
                return 2;
            }

            @Override
            public Phase phase() {
                return Phase.BEFORE;
            }

            @Override
            public boolean supports(OperationType type) {
                return true;
            }

            @Override
            public Object intercept(DataInvocation inv, Chain chain) throws Throwable {
                events.add("inner-before");
                Object result = chain.proceed(inv);
                events.add("inner-after");
                return result;
            }
        };

        InterceptorChain chain = new InterceptorChain(List.of(inner, outer));

        Object result = chain.proceed(new QueryInvocation(Object.class, null), inv -> {
            events.add("core");
            return "wrapped-result";
        });

        assertThat(result).isEqualTo("wrapped-result");
        assertThat(events).containsExactly(
                "outer-before",
                "inner-before",
                "core",
                "inner-after",
                "outer-after"
        );
    }

    @Test
    void beforeInterceptor_canShortCircuitCoreExecution() throws Throwable {
        DataInterceptor shortCircuit = new DataInterceptor() {
            @Override
            public int order() {
                return 1;
            }

            @Override
            public Phase phase() {
                return Phase.BEFORE;
            }

            @Override
            public boolean supports(OperationType type) {
                return true;
            }

            @Override
            public Object intercept(DataInvocation inv, Chain chain) {
                return "blocked";
            }
        };

        InterceptorChain chain = new InterceptorChain(List.of(shortCircuit));

        Object result = chain.proceed(new QueryInvocation(Object.class, null), inv -> "core-result");

        assertThat(result).isEqualTo("blocked");
    }

    @Test
    void interceptorThrows_exceptionPropagates() {
        DataInterceptor bad = new DataInterceptor() {
            @Override
            public int order() {
                return 1;
            }

            @Override
            public Phase phase() {
                return Phase.BEFORE;
            }

            @Override
            public boolean supports(OperationType type) {
                return true;
            }

            @Override
            public Object intercept(DataInvocation inv, Chain chain) throws Throwable {
                throw new RuntimeException("interceptor failed");
            }
        };
        InterceptorChain ic = new InterceptorChain(List.of(bad));
        assertThatThrownBy(() -> ic.proceed(new QueryInvocation(Object.class, null), i -> "x"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("interceptor failed");
    }
}
