package io.github.guanxiangkai.web.plus.security.client;

import io.github.guanxiangkai.web.plus.core.constants.AuthConstants;
import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 向明确允许的目标服务转发当前用户租户的 WebClient 过滤器。
 *
 * <p>过滤器不会注册为全局 WebClient 定制器。目标不在允许 Origin 集合内时，过滤器会移除已有
 * {@code X-Tenant-Id}，也不会读取当前用户；目标匹配时保留有效的显式租户头，否则在订阅时通过
 * {@link CurrentUserProvider#getCurrentUserMono()} 读取租户。</p>
 *
 * @author guanxiangkai
 */
public final class TenantForwardingExchangeFilterFunction implements ExchangeFilterFunction {

    private final CurrentUserProvider currentUserProvider;
    private final Set<Origin> allowedOrigins;

    /**
     * 创建租户转发过滤器。
     *
     * @param currentUserProvider 当前用户 SPI，只在允许目标且没有有效显式租户头时订阅读取
     * @param allowedOrigins 可接收租户头的 Origin，必须只有 scheme、host 与可用端口
     * @throws IllegalArgumentException Origin 含有 userinfo、路径、查询、片段或无效 scheme/host/port 时抛出
     */
    public TenantForwardingExchangeFilterFunction(CurrentUserProvider currentUserProvider,
                                                   Collection<URI> allowedOrigins) {
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider, "currentUserProvider");
        Objects.requireNonNull(allowedOrigins, "allowedOrigins");
        this.allowedOrigins = allowedOrigins.stream().map(Origin::from).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.defer(() -> {
            Origin requestOrigin = Origin.fromRequest(request.url());
            if (requestOrigin == null || !allowedOrigins.contains(requestOrigin)) {
                return next.exchange(withoutTenantHeader(request));
            }
            List<String> explicitValues = request.headers().get(AuthConstants.HeaderConstants.TENANT_ID);
            if (explicitValues != null && explicitValues.size() > 1) {
                throw new IllegalArgumentException("租户请求头必须只有一个值");
            }
            String explicitTenantId = request.headers().getFirst(AuthConstants.HeaderConstants.TENANT_ID);
            if (explicitTenantId != null) {
                validateTenantId(explicitTenantId);
            }
            if (StringUtils.hasText(explicitTenantId)) {
                return next.exchange(request);
            }
            return currentUserProvider.getCurrentUserMono()
                    .defaultIfEmpty(Optional.empty())
                    .flatMap(currentUser -> next.exchange(withTenantHeader(request, currentUser)));
        });
    }

    private ClientRequest withoutTenantHeader(ClientRequest request) {
        return ClientRequest.from(request)
                .headers(headers -> headers.remove(AuthConstants.HeaderConstants.TENANT_ID))
                .build();
    }

    private ClientRequest withTenantHeader(ClientRequest request, Optional<CurrentUser> currentUser) {
        String tenantId = currentUser.map(CurrentUser::tenantId).orElse(null);
        if (tenantId != null) {
            validateTenantId(tenantId);
        }
        return ClientRequest.from(request)
                .headers(headers -> {
                    headers.remove(AuthConstants.HeaderConstants.TENANT_ID);
                    if (StringUtils.hasText(tenantId)) {
                        headers.set(AuthConstants.HeaderConstants.TENANT_ID, tenantId);
                    }
                })
                .build();
    }

    private static void validateTenantId(String tenantId) {
        if (tenantId.chars().anyMatch(character -> character < 0x20 || character == 0x7F)) {
            throw new IllegalArgumentException("租户标识不能包含 HTTP 控制字符");
        }
    }

    private record Origin(String scheme, String host, int port) {

        static Origin from(URI uri) {
            Objects.requireNonNull(uri, "allowed origin");
            if (uri.isOpaque() || uri.getUserInfo() != null || hasPath(uri) || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("允许的租户转发 Origin 不能包含 userinfo、路径、查询或片段");
            }
            return fromParts(uri.getScheme(), uri.getHost(), uri.getPort());
        }

        static Origin fromRequest(URI uri) {
            if (uri == null || uri.isOpaque() || uri.getUserInfo() != null
                    || !StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return null;
            }
            String normalizedScheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return null;
            }
            int port = uri.getPort();
            if (port == 0 || port < -1 || port > 65535) {
                return null;
            }
            int normalizedPort = port == -1 ? ("https".equals(normalizedScheme) ? 443 : 80) : port;
            return new Origin(normalizedScheme, uri.getHost().toLowerCase(Locale.ROOT), normalizedPort);
        }

        private static Origin fromParts(String scheme, String host, int port) {
            if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
                throw new IllegalArgumentException("租户转发 Origin 必须包含 scheme 与 host");
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                throw new IllegalArgumentException("租户转发 Origin 仅支持 http 或 https");
            }
            if (port == 0 || port < -1 || port > 65535) {
                throw new IllegalArgumentException("租户转发 Origin 端口非法");
            }
            int normalizedPort = port == -1 ? ("https".equals(normalizedScheme) ? 443 : 80) : port;
            return new Origin(normalizedScheme, host.toLowerCase(Locale.ROOT), normalizedPort);
        }

        private static boolean hasPath(URI uri) {
            return StringUtils.hasText(uri.getPath()) || StringUtils.hasText(uri.getRawPath());
        }
    }
}
