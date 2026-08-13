package io.github.guanxiangkai.web.plus.web.config;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoEndpointRegistry;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoPublicConfig;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoRuntimePolicy;
import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoService;
import io.github.guanxiangkai.web.plus.web.filter.ApiCryptoWebFilter;
import io.github.guanxiangkai.web.plus.web.properties.ApiCryptoProperties;
import io.github.guanxiangkai.web.plus.web.properties.CorsProperties;
import io.github.guanxiangkai.web.plus.web.properties.ImportProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * web-plus-web 核心自动装配
 * <p>
 * 负责注册各模块共享的基础 Bean（如默认 {@link CurrentUserProvider}、CORS、Jackson 配置）。
 * 各能力模块的自动配置由各自 {@code AutoConfiguration.imports} 独立触发。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@AutoConfigureBefore(name = {
        "io.github.guanxiangkai.jpa.plus.starter.JpaPlusAutoConfiguration",
        "io.github.guanxiangkai.jpa.plus.starter.JpaPlusFieldAutoConfiguration",
        "io.github.guanxiangkai.jpa.plus.starter.JpaPlusInterceptorAutoConfiguration",
        "io.github.guanxiangkai.jpa.plus.starter.JpaPlusHibernateTenantAutoConfiguration"
})
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties({CorsProperties.class, ApiCryptoProperties.class, ImportProperties.class})
public class WebPlusCoreAutoConfiguration {

    /** API 加解密专用调度器 Bean 名称。 */
    public static final String API_CRYPTO_SCHEDULER_BEAN_NAME = "webPlusApiCryptoScheduler";

    public WebPlusCoreAutoConfiguration() {
        log.info("[web-plus] WebFlux 基础能力已启用");
    }

    /**
     * 默认 CurrentUserProvider —— 从 ThreadLocal 读取（已由认证过滤器填充）。
     * 若业务侧注册了自定义实现，此默认 Bean 自动退让。
     */
    @Bean
    @ConditionalOnMissingBean(CurrentUserProvider.class)
    public CurrentUserProvider defaultCurrentUserProvider() {
        return () -> Optional.ofNullable(CurrentUserHolder.get());
    }

    @Bean
    public WebFluxConfigurer webPlusWebFluxDateTimeConfigurer() {
        return new WebFluxConfigurer() {
            @Override
            public void addFormatters(FormatterRegistry registry) {
                registry.addConverter(new FlexibleLocalDateTimeConverter());
            }
        };
    }

    /**
     * API 加密公开配置端点。
     *
     * <p>前端启动时先读取该端点，再决定是否加密请求、是否解密响应；
     * 该端点不返回任何密钥。</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "apiCryptoPublicConfigRouterFunction")
    public RouterFunction<ServerResponse> apiCryptoPublicConfigRouterFunction(
            ApiCryptoProperties apiCryptoProperties,
            ApiCryptoEndpointRegistry apiCryptoEndpointRegistry) {
        return route(GET(ApiCryptoPublicConfig.CONFIG_PATH),
                ignored -> ServerResponse.ok()
                        .contentType(APPLICATION_JSON)
                        .bodyValue(ApiCryptoPublicConfig.from(apiCryptoProperties, apiCryptoEndpointRegistry.rules())));
    }

    /**
     * API 加密端点规则注册表。
     */
    @Bean
    @ConditionalOnMissingBean(ApiCryptoEndpointRegistry.class)
    public ApiCryptoEndpointRegistry apiCryptoEndpointRegistry(
            ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
        return new ApiCryptoEndpointRegistry(handlerMappings);
    }

    /**
     * API 加解密服务。
     *
     * <p>仅在 {@code web-plus.api-crypto.enabled=true} 时启用；
     * 默认关闭，避免对普通接口行为产生隐式影响。</p>
     */
    @Bean
    @ConditionalOnMissingBean(ApiCryptoService.class)
    @ConditionalOnProperty(prefix = "web-plus.api-crypto", name = "enabled", havingValue = "true")
    public ApiCryptoService apiCryptoService(ApiCryptoProperties apiCryptoProperties, ObjectMapper objectMapper) {
        return new ApiCryptoService(apiCryptoProperties, objectMapper);
    }

    /**
     * API 加解密聚合与执行边界。
     *
     * <p>配置在创建过滤器前转换为不可变策略并完成校验，避免非法上限进入运行期。</p>
     */
    @Bean
    @ConditionalOnMissingBean(ApiCryptoRuntimePolicy.class)
    @ConditionalOnProperty(prefix = "web-plus.api-crypto", name = "enabled", havingValue = "true")
    public ApiCryptoRuntimePolicy apiCryptoRuntimePolicy(ApiCryptoProperties apiCryptoProperties) {
        if (apiCryptoProperties.getRuntime() == null) {
            throw new IllegalArgumentException("web-plus.api-crypto.runtime 不能为空");
        }
        return apiCryptoProperties.getRuntime().toPolicy();
    }

    /**
     * API 加解密专用有界工作池。
     *
     * <p>PBKDF2、JSON 编解码与对称加密不会占用 Netty 事件循环；有界队列在过载时快速失败。</p>
     */
    @Bean(name = API_CRYPTO_SCHEDULER_BEAN_NAME, destroyMethod = "dispose")
    @ConditionalOnMissingBean(name = API_CRYPTO_SCHEDULER_BEAN_NAME)
    @ConditionalOnProperty(prefix = "web-plus.api-crypto", name = "enabled", havingValue = "true")
    public Scheduler apiCryptoScheduler(ApiCryptoRuntimePolicy runtimePolicy) {
        return Schedulers.newBoundedElastic(
                runtimePolicy.workerCount(),
                runtimePolicy.taskQueueCapacity(),
                "web-plus-api-crypto");
    }

    /**
     * API 入参解密与出参加密过滤器。
     */
    @Bean
    @ConditionalOnMissingBean(ApiCryptoWebFilter.class)
    @ConditionalOnBean(ApiCryptoService.class)
    @ConditionalOnProperty(prefix = "web-plus.api-crypto", name = "enabled", havingValue = "true")
    public ApiCryptoWebFilter apiCryptoWebFilter(
            ApiCryptoService apiCryptoService,
            ObjectMapper objectMapper,
            ApiCryptoEndpointRegistry apiCryptoEndpointRegistry,
            ApiCryptoRuntimePolicy runtimePolicy,
            @Qualifier(API_CRYPTO_SCHEDULER_BEAN_NAME) Scheduler cryptoScheduler) {
        return new ApiCryptoWebFilter(
                apiCryptoService,
                objectMapper,
                apiCryptoEndpointRegistry,
                runtimePolicy,
                cryptoScheduler);
    }

    /**
     * 全局 CORS 跨域过滤器（可通过 web-plus.cors.enabled=false 关闭）。
     */
    @Bean
    @ConditionalOnMissingBean(CorsWebFilter.class)
    @ConditionalOnProperty(prefix = "web-plus.cors", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CorsWebFilter corsWebFilter(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();

        if (corsProperties.allowedOrigins() != null && !corsProperties.allowedOrigins().isEmpty()) {
            corsProperties.allowedOrigins().forEach(config::addAllowedOrigin);
        }
        if (corsProperties.allowedOriginPatterns() != null) {
            corsProperties.allowedOriginPatterns().forEach(config::addAllowedOriginPattern);
        }
        if (corsProperties.allowedMethods() != null) {
            corsProperties.allowedMethods().forEach(config::addAllowedMethod);
        }
        if (corsProperties.allowedHeaders() != null) {
            corsProperties.allowedHeaders().forEach(config::addAllowedHeader);
        }
        if (corsProperties.exposedHeaders() != null) {
            corsProperties.exposedHeaders().forEach(config::addExposedHeader);
        }
        config.setAllowCredentials(corsProperties.allowCredentials());
        config.setMaxAge(corsProperties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        log.debug("[web-plus] CORS 已配置: allowedOrigins={}", corsProperties.allowedOrigins());
        return new CorsWebFilter(source);
    }

    /**
     * jpa-plus 当前用户桥接配置——仅在 classpath 中存在 jpa-plus-field 时激活。
     * <p>
     * 嵌套 {@code @Configuration} 类保证：当 jpa-plus 不在运行时 classpath 时，
     * Spring Boot 不会尝试解析 jpa-plus 类型，避免 ClassNotFoundException。
     * </p>
     */
    @ConditionalOnClass(name = "io.github.guanxiangkai.jpa.plus.field.autofill.spi.CurrentUserProvider")
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class JpaCurrentUserProviderBridgeConfiguration {

        /**
         * 将 jpa-plus {@code @CreateBy} / {@code @UpdateBy} 自动填充 SPI 桥接到 web-plus {@link CurrentUserProvider}。
         * <p>
         * 业务侧如需自定义（如额外注入 tenantId / deptId），注册自定义
         * {@code io.github.guanxiangkai.jpa.plus.field.autofill.spi.CurrentUserProvider} Bean 即可覆盖。
         * </p>
         */
        @Bean
        @ConditionalOnMissingBean(
                name = "jpaCurrentUserProvider",
                value = io.github.guanxiangkai.jpa.plus.field.autofill.spi.CurrentUserProvider.class)
        public io.github.guanxiangkai.jpa.plus.field.autofill.spi.CurrentUserProvider jpaCurrentUserProvider(
                ObjectProvider<CurrentUserProvider> webPlusProviderProvider) {
            CurrentUserProvider webPlusProvider = webPlusProviderProvider
                    .getIfAvailable(() -> () -> Optional.ofNullable(CurrentUserHolder.get()));
            log.debug("[web-plus] 注册 jpa-plus CurrentUserProvider 桥接（委托给 web-plus CurrentUserHolder）");
            return webPlusProvider::getCurrentUserId;
        }
    }

    /**
     * jpa-plus 多租户来源桥接配置——仅在 classpath 中存在 jpa-plus-interceptor 时激活。
     * <p>
     * 自动将 web-plus 当前用户 SPI 中的 {@code tenantId} 桥接为 jpa-plus
     * {@code TenantIdProvider}，由 jpa-plus 负责 QueryWrapper 拦截器和 Hibernate Filter
     * 两层租户隔离，不在 web-plus 中重复实现查询条件拼接。
     * </p>
     * <p>
     * 业务侧如需自定义租户 ID 来源（例如从 MDC、Header 或其他上下文读取），
     * 只需注册自定义 {@code TenantIdProvider} Bean 覆盖此默认实现即可。
     * </p>
     */
    @ConditionalOnClass(name = "io.github.guanxiangkai.jpa.plus.interceptor.tenant.spi.TenantIdProvider")
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class JpaPlusTenantIdProviderBridgeConfiguration {

        @Bean("tenantIdProvider")
        @ConditionalOnMissingBean(io.github.guanxiangkai.jpa.plus.interceptor.tenant.spi.TenantIdProvider.class)
        public io.github.guanxiangkai.jpa.plus.interceptor.tenant.spi.TenantIdProvider jpaPlusTenantIdProvider(
                ObjectProvider<CurrentUserProvider> currentUserProviderProvider) {
            CurrentUserProvider webPlusProvider = currentUserProviderProvider
                    .getIfAvailable(() -> () -> Optional.ofNullable(CurrentUserHolder.get()));
            log.debug("[web-plus] 注册 jpa-plus TenantIdProvider 桥接（委托给 web-plus CurrentUserProvider）");
            return () -> webPlusProvider.getCurrentUser()
                    .map(CurrentUser::tenantId)
                    .filter(StringUtils::hasText)
                    .orElse(null);
        }
    }

    private static final class FlexibleLocalDateTimeConverter implements Converter<String, LocalDateTime> {

        private static final List<DateTimeFormatter> FORMATTERS = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        );

        @Override
        public LocalDateTime convert(String source) {
            if (source == null || source.isBlank()) {
                return null;
            }
            String value = source.trim();
            for (DateTimeFormatter formatter : FORMATTERS) {
                try {
                    return LocalDateTime.parse(value, formatter);
                } catch (DateTimeParseException ignored) {
                    // 尝试下一个支持的前端时间格式。
                }
            }
            throw new IllegalArgumentException("Invalid LocalDateTime value: " + source);
        }
    }
}
