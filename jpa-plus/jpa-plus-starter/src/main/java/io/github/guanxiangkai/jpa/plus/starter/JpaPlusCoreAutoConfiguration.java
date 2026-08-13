package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.core.executor.DefaultJpaPlusExecutor;
import io.github.guanxiangkai.jpa.plus.core.executor.JpaPlusExecutor;
import io.github.guanxiangkai.jpa.plus.core.field.FieldEngine;
import io.github.guanxiangkai.jpa.plus.core.interceptor.DataInterceptor;
import io.github.guanxiangkai.jpa.plus.core.interceptor.InterceptorChain;
import io.github.guanxiangkai.jpa.plus.core.interceptor.InterceptorChainContributor;
import io.github.guanxiangkai.jpa.plus.core.metrics.JpaPlusMetrics;
import io.github.guanxiangkai.jpa.plus.core.model.DeleteInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.QueryInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.SaveInvocation;
import io.github.guanxiangkai.jpa.plus.core.spi.JpaPlusLoader;
import io.github.guanxiangkai.jpa.plus.query.context.FlushMode;
import io.github.guanxiangkai.jpa.plus.query.context.FlushStrategy;
import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;
import io.github.guanxiangkai.jpa.plus.query.executor.QueryContextExecutor;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Plus 核心执行器自动装配
 *
 * <p>负责注册：{@link InterceptorChain}、{@link FlushStrategy}、{@link JpaPlusExecutor}。</p>
 * <p>应用关闭时自动清理 SPI 缓存，防止 ClassLoader 泄漏。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration
@Slf4j
public class JpaPlusCoreAutoConfiguration {

    // ─────────── SPI 缓存生命周期管理 ───────────

    @PreDestroy
    void cleanupSpiCache() {
        JpaPlusLoader.invalidateAll();
    }

    // ─────────── Flush 策略 ───────────

    @Bean
    @ConditionalOnMissingBean
    FlushStrategy flushStrategy(
            @Value("${jpa-plus.flush-mode:AUTO}") FlushMode flushMode) {
        return new FlushStrategy(flushMode);
    }

    // ─────────── 拦截器链（含 SPI 贡献者） ───────────

    @Bean
    @ConditionalOnMissingBean
    InterceptorChain interceptorChain(List<DataInterceptor> interceptors,
                                      List<InterceptorChainContributor> beanContributors) {
        List<DataInterceptor> all = new ArrayList<>(interceptors != null ? interceptors : List.of());

        List<InterceptorChainContributor> contributors = new ArrayList<>(
                beanContributors != null ? beanContributors : List.of());
        contributors.addAll(JpaPlusLoader.loadAll(InterceptorChainContributor.class));

        for (InterceptorChainContributor contributor : contributors) {
            List<DataInterceptor> contributed = contributor.contribute();
            if (contributed != null) {
                all.addAll(contributed);
            }
        }

        return new InterceptorChain(all);
    }

    // ─────────── 统一执行器（含 Micrometer 指标） ───────────

    @Bean
    @ConditionalOnMissingBean
    JpaPlusExecutor jpaPlusExecutor(InterceptorChain interceptorChain,
                                    FieldEngine fieldEngine,
                                    QueryContextExecutor queryContextExecutor,
                                    EntityManager entityManager,
                                    FlushStrategy flushStrategy,
                                    ObjectProvider<JpaPlusMetrics> metricsProvider) {
        InterceptorChain.CoreExecution coreExecution = invocation -> switch (invocation) {
            case QueryInvocation qi -> {
                flushStrategy.flushIfNeeded(entityManager);
                if (qi.queryContext() instanceof QueryContext queryContext) {
                    yield queryContextExecutor.list(queryContext, qi.entityClass());
                }
                log.warn("[jpa-plus] Unknown queryContext type for QueryInvocation: {}, returning empty list",
                        qi.queryContext() != null ? qi.queryContext().getClass().getName() : "null");
                yield List.of();
            }
            case SaveInvocation si -> {
                if (si.entity() != null) {
                    yield entityManager.merge(si.entity());
                }
                yield null;
            }
            case DeleteInvocation di -> {
                if (di.entity() != null) {
                    entityManager.remove(
                            entityManager.contains(di.entity())
                                    ? di.entity()
                                    : entityManager.merge(di.entity())
                    );
                }
                yield null;
            }
        };

        JpaPlusMetrics metrics = metricsProvider.getIfAvailable(() -> JpaPlusMetrics.NOOP);
        return new DefaultJpaPlusExecutor(interceptorChain, fieldEngine, coreExecution, metrics);
    }
}
