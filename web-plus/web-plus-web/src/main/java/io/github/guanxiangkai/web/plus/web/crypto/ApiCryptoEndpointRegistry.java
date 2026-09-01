package io.github.guanxiangkai.web.plus.web.crypto;

import io.github.guanxiangkai.web.plus.web.annotation.ApiCrypto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * API 加密端点规则注册表。
 *
 * <p>从 WebFlux Controller 映射中扫描 {@link ApiCrypto} 注解。服务端使用完整的
 * {@link RequestMappingInfo} 条件和 Spring 特异性排序定位实际端点，公开规则仅用于向客户端
 * 描述显式加密端点，不能替代服务端路由判断。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class ApiCryptoEndpointRegistry {

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;
    private volatile RegistrySnapshot cachedSnapshot;

    public ApiCryptoEndpointRegistry(ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    public List<ApiCryptoEndpointRule> rules() {
        return snapshot().rules();
    }

    /**
     * 按 Spring WebFlux 的完整映射条件查找当前实际端点的加密规则。
     *
     * <p>同一路径可按请求参数、请求头、消费媒体类型、响应媒体类型或自定义条件映射到
     * 不同方法。注册表先在每个有序 HandlerMapping 内选择最具体的实际端点；端点匹配结果再
     * 单独表明是否存在 {@link ApiCrypto}，不会被同路径的其他加密端点误拦截。</p>
     *
     * @param exchange 当前请求交换对象
     * @return 实际端点匹配结果；不存在匹配端点时为空
     */
    public Optional<EndpointMatch> find(ServerWebExchange exchange) {
        for (MappingGroup group : snapshot().groups()) {
            List<MatchedEndpoint> matches = group.endpoints().stream()
                    .map(endpoint -> endpoint.match(exchange))
                    .flatMap(Optional::stream)
                    .toList();
            if (!matches.isEmpty()) {
                return Optional.of(new EndpointMatch(selectBest(matches, exchange).endpoint()));
            }
        }
        return Optional.empty();
    }

    /**
     * 查找等待解密查询参数后才能确定的加密端点候选。
     *
     * <p>候选匹配只忽略 {@code params} 条件，仍校验 HTTP 方法、路径、请求头、消费与响应媒体
     * 类型、API 版本和自定义条件，并且只包含显式启用请求解密的端点。调用方必须在解密后再次
     * 调用 {@link #find(ServerWebExchange)}，并使用返回对象的 {@link EncryptedQueryCandidates#accepts}
     * 复核最终端点，禁止把解密后的请求交给未标注端点。</p>
     *
     * @param exchange 包含加密查询信封的原始请求交换对象
     * @return 首个有序 HandlerMapping 内所有满足非参数条件的加密候选；没有候选时为空
     */
    public Optional<EncryptedQueryCandidates> findEncryptedQueryCandidates(ServerWebExchange exchange) {
        for (MappingGroup group : snapshot().groups()) {
            List<MatchedEndpoint> matches = group.endpoints().stream()
                    .filter(endpoint -> endpoint.rule().map(ApiCryptoEndpointRule::request).orElse(false))
                    .map(endpoint -> endpoint.matchIgnoringParams(exchange))
                    .flatMap(Optional::stream)
                    .toList();
            if (!matches.isEmpty()) {
                return Optional.of(new EncryptedQueryCandidates(matches.stream()
                        .map(MatchedEndpoint::endpoint)
                        .toList()));
            }
        }
        return Optional.empty();
    }

    private RegistrySnapshot snapshot() {
        RegistrySnapshot snapshot = cachedSnapshot;
        if (snapshot == null) {
            synchronized (this) {
                snapshot = cachedSnapshot;
                if (snapshot == null) {
                    snapshot = scanMappings();
                    cachedSnapshot = snapshot;
                }
            }
        }
        return snapshot;
    }

    private MatchedEndpoint selectBest(List<MatchedEndpoint> matches, ServerWebExchange exchange) {
        Comparator<MatchedEndpoint> comparator = (left, right) ->
                left.matchingInfo().compareTo(right.matchingInfo(), exchange);
        List<MatchedEndpoint> sortedMatches = matches.stream().sorted(comparator).toList();
        MatchedEndpoint best = sortedMatches.getFirst();
        if (sortedMatches.size() > 1 && comparator.compare(best, sortedMatches.get(1)) == 0) {
            throw new IllegalStateException(
                    "存在无法区分的 WebFlux 端点映射，无法确定 API 加密规则: "
                            + best.endpoint().handlerMethod() + " 与 "
                            + sortedMatches.get(1).endpoint().handlerMethod());
        }
        return best;
    }

    private RegistrySnapshot scanMappings() {
        List<MappingGroup> groups = handlerMappings.orderedStream()
                .map(handlerMapping -> new MappingGroup(handlerMapping.getHandlerMethods().entrySet().stream()
                        .map(entry -> new RegisteredEndpoint(
                                entry.getKey(),
                                entry.getKey().mutate().params().build(),
                                entry.getValue(),
                                resolveAnnotation(entry.getValue())
                                        .map(annotation -> toRule(entry.getKey(), annotation))))
                        .toList()))
                .toList();
        List<ApiCryptoEndpointRule> rules = groups.stream()
                .flatMap(group -> group.endpoints().stream())
                .flatMap(endpoint -> endpoint.rule().stream())
                .sorted(Comparator
                        .comparing((ApiCryptoEndpointRule rule) -> String.join(",", rule.patterns()))
                        .thenComparing(rule -> String.join(",", rule.methods())))
                .distinct()
                .toList();
        return new RegistrySnapshot(groups, rules);
    }

    private ApiCryptoEndpointRule toRule(RequestMappingInfo mappingInfo, ApiCrypto annotation) {
        List<String> methods = mappingInfo.getMethodsCondition().getMethods().stream()
                .map(RequestMethod::name)
                .sorted()
                .toList();
        List<String> patterns = mappingInfo.getPatternsCondition().getPatterns().stream()
                .map(Object::toString)
                .sorted()
                .toList();
        return new ApiCryptoEndpointRule(methods, patterns, annotation.request(), annotation.response());
    }

    private Optional<ApiCrypto> resolveAnnotation(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        ApiCrypto methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, ApiCrypto.class);
        if (methodAnnotation != null) {
            return Optional.of(methodAnnotation);
        }
        return Optional.ofNullable(
                AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ApiCrypto.class));
    }

    /**
     * Spring 完整映射条件选中的实际端点。
     */
    public static final class EndpointMatch {

        private final RegisteredEndpoint endpoint;

        private EndpointMatch(RegisteredEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        /**
         * 返回实际端点显式声明的加密规则。
         *
         * @return 端点未标注 {@link ApiCrypto} 时为空
         */
        public Optional<ApiCryptoEndpointRule> rule() {
            return endpoint.rule();
        }
    }

    /**
     * 解密查询参数前按非参数条件筛选出的受限加密端点集合。
     */
    public static final class EncryptedQueryCandidates {

        private final List<RegisteredEndpoint> endpoints;

        private EncryptedQueryCandidates(List<RegisteredEndpoint> endpoints) {
            this.endpoints = endpoints;
        }

        /**
         * 验证解密后的完整路由结果仍属于原始请求允许的加密候选。
         *
         * @param match 解密后按完整 Spring 条件选中的端点
         * @return 最终端点属于候选且仍显式启用请求解密时为 {@code true}
         */
        public boolean accepts(EndpointMatch match) {
            return match != null
                    && endpoints.contains(match.endpoint)
                    && match.rule().map(ApiCryptoEndpointRule::request).orElse(false);
        }
    }

    private record RegistrySnapshot(
            List<MappingGroup> groups,
            List<ApiCryptoEndpointRule> rules
    ) {
    }

    private record MappingGroup(List<RegisteredEndpoint> endpoints) {
    }

    private record RegisteredEndpoint(
            RequestMappingInfo mappingInfo,
            RequestMappingInfo mappingInfoWithoutParams,
            HandlerMethod handlerMethod,
            Optional<ApiCryptoEndpointRule> rule
    ) {

        private Optional<MatchedEndpoint> match(ServerWebExchange exchange) {
            return Optional.ofNullable(mappingInfo.getMatchingCondition(exchange))
                    .map(matchingInfo -> new MatchedEndpoint(this, matchingInfo));
        }

        private Optional<MatchedEndpoint> matchIgnoringParams(ServerWebExchange exchange) {
            return Optional.ofNullable(mappingInfoWithoutParams.getMatchingCondition(exchange))
                    .map(matchingInfo -> new MatchedEndpoint(this, matchingInfo));
        }
    }

    private record MatchedEndpoint(
            RegisteredEndpoint endpoint,
            RequestMappingInfo matchingInfo
    ) {
    }
}
