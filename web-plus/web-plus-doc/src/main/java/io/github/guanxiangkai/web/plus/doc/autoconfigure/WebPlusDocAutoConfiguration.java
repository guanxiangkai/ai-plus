package io.github.guanxiangkai.web.plus.doc.autoconfigure;

import io.github.guanxiangkai.web.plus.doc.customizer.ErrorCodeOpenApiCustomizer;
import io.github.guanxiangkai.web.plus.doc.customizer.WebPlusOpenApiCustomizer;
import io.github.guanxiangkai.web.plus.doc.properties.DocProperties;
import io.github.guanxiangkai.web.plus.doc.spi.ErrorCodeDocumentContributor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

/**
 * web-plus-doc 自动装配（SpringDoc WebFlux）
 *
 * <h3>API 分组</h3>
 * <p>
 * 框架默认注册"全部接口"分组（匹配 {@code /**}）。
 * 业务项目可通过 {@code web-plus.doc.groups} 配置自定义分组，也可直接注册 {@link GroupedOpenApi} Bean。
 * </p>
 *
 * <h3>扩展点（SPI）</h3>
 * <ul>
 *   <li>{@link WebPlusOpenApiCustomizer} —— 自定义 OpenAPI 对象（策略模式，多个按 order 排序）</li>
 *   <li>{@link ErrorCodeDocumentContributor} —— 向文档注入模块错误码说明</li>
 * </ul>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnProperty(prefix = "web-plus.doc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DocProperties.class)
public class WebPlusDocAutoConfiguration {

    private final DocProperties properties;

    public WebPlusDocAutoConfiguration(DocProperties properties) {
        this.properties = properties;
        log.info("[web-plus] Doc 模块已启用，title={}", properties.title());
    }

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI openAPI(List<WebPlusOpenApiCustomizer> customizers) {
        OpenAPI openAPI = new OpenAPI()
                .info(buildInfo())
                .servers(List.of(buildServer()))
                .externalDocs(new ExternalDocumentation()
                        .description("Web Plus 文档")
                        .url(properties.contact().url()))
                .components(new Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Bearer Token 认证，格式：Bearer <token>")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"));

        // 应用所有自定义器（策略模式）
        customizers.stream()
                .sorted(Comparator.comparingInt(WebPlusOpenApiCustomizer::getOrder))
                .forEach(c -> c.customize(openAPI));

        return openAPI;
    }

    /**
     * 默认"全部接口"分组 —— 若业务侧已定义同名 Bean 则跳过
     */
    @Bean("webPlusAllApiGroup")
    @ConditionalOnMissingBean(name = "webPlusAllApiGroup")
    public GroupedOpenApi allApiGroup() {
        return GroupedOpenApi.builder()
                .group("全部接口")
                .pathsToMatch("/**")
                .build();
    }

    /**
     * 从 {@code web-plus.doc.groups} 配置动态注册 {@link GroupedOpenApi} Bean。
     *
     * <p>每个 GroupConfig 对应一个 SpringDoc 分组，Bean 名称为 {@code "docGroup_" + 分组名}。</p>
     *
     * <pre>{@code
     * web-plus:
     *   doc:
     *     groups:
     *       - name: 用户模块
     *         paths-to-match: /user/**, /auth/**
     *       - name: 订单模块
     *         paths-to-match: /order/**
     * }</pre>
     */
    @Bean
    public ConfigGroupRegistrar configGroupRegistrar(DocProperties docProperties) {
        return new ConfigGroupRegistrar(docProperties.groups());
    }

    /**
     * 错误码文档聚合自定义器（有 contributor 时才注册）
     */
    @Bean
    @ConditionalOnMissingBean(ErrorCodeOpenApiCustomizer.class)
    public ErrorCodeOpenApiCustomizer errorCodeOpenApiCustomizer(
            List<ErrorCodeDocumentContributor> contributors) {
        return new ErrorCodeOpenApiCustomizer(contributors);
    }

    // ── 私有工具 ─────────────────────────────────────────────────

    private Info buildInfo() {
        var c = properties.contact();
        return new Info()
                .title(properties.title())
                .version(properties.version())
                .description(properties.description())
                .contact(new Contact().name(c.name()).email(c.email()).url(c.url()))
                .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0"));
    }

    private Server buildServer() {
        var s = properties.server();
        return new Server().url(s.url()).description(s.description());
    }

    // ── 内部：通过 BeanDefinitionRegistrar 动态注册分组 Bean ─────

    /**
     * 将 {@code web-plus.doc.groups} 配置动态注册为 {@link GroupedOpenApi} Bean。
     *
     * <p>使用 {@link org.springframework.beans.factory.InitializingBean} 方式而非
     * {@link ImportBeanDefinitionRegistrar}，避免在配置属性尚未绑定时就尝试读取 groups。</p>
     */
    public static class ConfigGroupRegistrar implements org.springframework.beans.factory.InitializingBean,
            org.springframework.beans.factory.BeanFactoryAware {

        private final List<DocProperties.GroupConfig> groups;
        private org.springframework.beans.factory.support.BeanDefinitionRegistry registry;

        public ConfigGroupRegistrar(List<DocProperties.GroupConfig> groups) {
            this.groups = groups != null ? groups : List.of();
        }

        @Override
        public void setBeanFactory(org.springframework.beans.factory.BeanFactory beanFactory) {
            if (beanFactory instanceof BeanDefinitionRegistry r) {
                this.registry = r;
            }
        }

        @Override
        public void afterPropertiesSet() {
            if (registry == null || groups.isEmpty()) return;
            for (DocProperties.GroupConfig cfg : groups) {
                if (!StringUtils.hasText(cfg.name())) continue;
                String beanName = "docGroup_" + cfg.name();
                if (registry.containsBeanDefinition(beanName)) continue;

                String[] paths = cfg.pathsToMatch().toArray(String[]::new);
                GroupedOpenApi group = GroupedOpenApi.builder()
                        .group(cfg.name())
                        .pathsToMatch(paths)
                        .build();

                RootBeanDefinition bd = new RootBeanDefinition(GroupedOpenApi.class, () -> group);
                bd.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
                registry.registerBeanDefinition(beanName, bd);
                log.debug("[web-plus] 注册 API 分组: name={} paths={}", cfg.name(), cfg.pathsToMatch());
            }
        }
    }
}
