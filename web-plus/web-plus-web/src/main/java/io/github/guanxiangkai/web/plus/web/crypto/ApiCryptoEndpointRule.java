package io.github.guanxiangkai.web.plus.web.crypto;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.util.List;

/**
 * API 加密端点规则。
 *
 * <p>由 {@code @ApiCrypto} 注解生成，并通过公开配置端点下发给客户端。该模型只包含
 * HTTP 方法、路径和加密方向摘要；服务端使用 Spring WebFlux 的完整映射条件定位实际端点，
 * 不依据本摘要单独决定请求是否加密。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@RegisterReflectionForBinding
public record ApiCryptoEndpointRule(
        List<String> methods,
        List<String> patterns,
        boolean request,
        boolean response
) {
}
