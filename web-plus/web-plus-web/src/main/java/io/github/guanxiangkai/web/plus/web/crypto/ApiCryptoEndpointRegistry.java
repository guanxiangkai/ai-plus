package io.github.guanxiangkai.web.plus.web.crypto;

import io.github.guanxiangkai.web.plus.web.annotation.ApiCrypto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * API 加密端点规则注册表。
 *
 * <p>从 WebFlux Controller 映射中扫描 {@link ApiCrypto} 注解，形成后端和前端共享的规则列表。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class ApiCryptoEndpointRegistry {

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;
    private final PathPatternParser pathPatternParser = new PathPatternParser();
    private volatile List<ApiCryptoEndpointRule> cachedRules;

    public ApiCryptoEndpointRegistry(ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    public List<ApiCryptoEndpointRule> rules() {
        List<ApiCryptoEndpointRule> rules = cachedRules;
        if (rules == null) {
            synchronized (this) {
                rules = cachedRules;
                if (rules == null) {
                    rules = scanRules();
                    cachedRules = rules;
                }
            }
        }
        return rules;
    }

    public Optional<ApiCryptoEndpointRule> find(ServerHttpRequest request) {
        String method = request.getMethod().name().toUpperCase(Locale.ROOT);
        PathContainer path = request.getPath().pathWithinApplication();

        return rules().stream()
                .filter(rule -> matchesMethod(rule, method))
                .filter(rule -> matchesPath(rule, path))
                .findFirst();
    }

    private List<ApiCryptoEndpointRule> scanRules() {
        List<ApiCryptoEndpointRule> rules = new ArrayList<>();
        handlerMappings.orderedStream().forEach(handlerMapping ->
                handlerMapping.getHandlerMethods().forEach((mappingInfo, handlerMethod) ->
                        resolveAnnotation(handlerMethod).ifPresent(annotation -> {
                            List<String> methods = mappingInfo.getMethodsCondition().getMethods().stream()
                                    .map(RequestMethod::name)
                                    .sorted()
                                    .toList();
                            List<String> patterns = mappingInfo.getPatternsCondition().getPatterns().stream()
                                    .map(Object::toString)
                                    .filter(pattern -> !isInternalPathPattern(pattern))
                                    .sorted()
                                    .toList();
                            if (!patterns.isEmpty()) {
                                rules.add(new ApiCryptoEndpointRule(
                                        methods,
                                        patterns,
                                        annotation.request(),
                                        annotation.response()
                                ));
                            }
                        })));

        return rules.stream()
                .sorted(Comparator
                        .comparing((ApiCryptoEndpointRule rule) -> String.join(",", rule.patterns()))
                        .thenComparing(rule -> String.join(",", rule.methods())))
                .toList();
    }

    private Optional<ApiCrypto> resolveAnnotation(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        ApiCrypto methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, ApiCrypto.class);
        if (methodAnnotation != null) {
            return Optional.of(methodAnnotation);
        }
        return Optional.ofNullable(AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ApiCrypto.class));
    }

    private boolean isInternalPathPattern(String pattern) {
        return "/internal".equals(pattern) || pattern.startsWith("/internal/");
    }

    private boolean matchesMethod(ApiCryptoEndpointRule rule, String method) {
        return rule.methods().isEmpty() || rule.methods().stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .anyMatch(method::equals);
    }

    private boolean matchesPath(ApiCryptoEndpointRule rule, PathContainer path) {
        Set<String> patterns = new LinkedHashSet<>(rule.patterns());
        return patterns.stream()
                .map(pathPatternParser::parse)
                .anyMatch(pattern -> pattern.matches(path));
    }
}
