package io.github.guanxiangkai.web.plus.web.crypto;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.util.List;

/**
 * API 加密端点规则。
 *
 * <p>由 {@code @ApiCrypto} 注解生成，用于后端过滤器匹配请求，也会通过公开配置端点下发给前端。</p>
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
