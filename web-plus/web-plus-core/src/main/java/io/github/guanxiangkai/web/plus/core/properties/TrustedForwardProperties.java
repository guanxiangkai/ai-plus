package io.github.guanxiangkai.web.plus.core.properties;

import io.github.guanxiangkai.web.plus.core.constants.AuthConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 受信任身份透传配置。
 * <p>
 * 网关与内部 HTTP 客户端会附带该令牌，下游服务在信任 X-User-* 请求头前必须先校验此令牌。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "web-plus.security.trusted-forward")
public class TrustedForwardProperties {

    /**
     * 透传令牌请求头名称。
     */
    private String headerName = AuthConstants.HeaderConstants.TRUSTED_FORWARD_TOKEN;

    /**
     * 透传令牌值。
     */
    private String token;

    public void validateConfigured(String componentName) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException(componentName
                    + " 未配置 web-plus.security.trusted-forward.token"
                    + "（可通过 Nacos、AI_SECURITY_TRUSTED_FORWARD_TOKEN 或 TRUSTED_FORWARD_TOKEN 配置），"
                    + "已拒绝启动以避免身份透传被伪造");
        }
    }

    public boolean matches(String candidate) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(candidate)) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8)
        );
    }
}
