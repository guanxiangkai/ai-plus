package io.github.guanxiangkai.web.plus.web.crypto;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

/**
 * API 加密信封。
 *
 * <p>字段定义独立于具体产品客户端，支持由应用按需实现的同一加密信封协议。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@RegisterReflectionForBinding
public record ApiCryptoEnvelope(
        boolean encrypted,
        String version,
        String algorithm,
        String keyId,
        String iv,
        String salt,
        String data,
        String tag
) {
}
