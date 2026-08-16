package io.github.guanxiangkai.web.plus.security.client;

import io.github.guanxiangkai.web.plus.core.constants.AuthConstants;
import io.github.guanxiangkai.web.plus.security.util.SecurityUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 将当前租户上下文透传到下游 WebClient 请求。
 *
 * <p>显式请求头优先级最高，其次读取 Web Plus 安全上下文，最后依次读取调用方提供的
 * 后台任务租户解析器。该顺序可避免覆盖显式跨租户调用，同时让请求线程和后台任务使用
 * 同一个过滤器契约。</p>
 *
 * <p>本实现为普通显式对象，不依赖全局 Bean 后处理器，适用于 Spring AOT 与原生镜像分析。</p>
 *
 * @author guanxiangkai
 * @since 1.1.0
 */
public final class TenantForwardingExchangeFilterFunction implements ExchangeFilterFunction {

    private final List<Supplier<String>> fallbackTenantIdSuppliers;

    /**
     * 创建租户透传过滤器。
     *
     * @param fallbackTenantIdSuppliers 安全上下文为空时按顺序调用的租户解析器
     */
    @SafeVarargs
    public TenantForwardingExchangeFilterFunction(Supplier<String>... fallbackTenantIdSuppliers) {
        Objects.requireNonNull(fallbackTenantIdSuppliers, "租户解析器数组不能为空");
        this.fallbackTenantIdSuppliers = List.of(fallbackTenantIdSuppliers);
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        Objects.requireNonNull(request, "请求不能为空");
        Objects.requireNonNull(next, "下游交换函数不能为空");

        String explicitTenantId = normalize(request.headers().getFirst(
                AuthConstants.HeaderConstants.TENANT_ID));
        if (explicitTenantId != null) {
            return next.exchange(request);
        }

        String tenantId = resolveTenantId();
        if (tenantId == null) {
            return next.exchange(request);
        }

        ClientRequest forwarded = ClientRequest.from(request)
                .headers(headers -> headers.set(AuthConstants.HeaderConstants.TENANT_ID, tenantId))
                .build();
        return next.exchange(forwarded);
    }

    private String resolveTenantId() {
        String tenantId = normalize(SecurityUtils.getTenantId());
        if (tenantId != null) {
            return tenantId;
        }
        for (Supplier<String> supplier : fallbackTenantIdSuppliers) {
            tenantId = normalize(Objects.requireNonNull(supplier, "租户解析器不能为空").get());
            if (tenantId != null) {
                return tenantId;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
