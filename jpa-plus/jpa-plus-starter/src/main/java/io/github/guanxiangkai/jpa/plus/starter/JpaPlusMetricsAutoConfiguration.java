package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.core.metrics.JpaPlusMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * JPA Plus Micrometer 指标自动装配。
 *
 * <p>该配置与核心自动装配保持独立，使未引入 Micrometer 的应用无需加载任何
 * {@link MeterRegistry} 类型。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration(after = JpaPlusAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
public class JpaPlusMetricsAutoConfiguration {

    /**
     * 创建指标采集器。
     *
     * @param registry Micrometer 指标注册表
     * @param prefix 指标名称前缀
     * @return JPA Plus 指标采集器
     */
    @Bean
    @ConditionalOnMissingBean(JpaPlusMetrics.class)
    @ConditionalOnBean(MeterRegistry.class)
    JpaPlusMetrics jpaPlusMetrics(
            MeterRegistry registry,
            @Value("${jpa-plus.metrics.prefix:jpa.plus}") String prefix) {
        return new MicrometerJpaPlusMetrics(registry, prefix);
    }
}
