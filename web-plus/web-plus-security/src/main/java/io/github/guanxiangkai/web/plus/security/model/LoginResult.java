package io.github.guanxiangkai.web.plus.security.model;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

/**
 * 登录结果模型
 * <p>
 * 登录成功后返回给客户端，包含 Access Token 与当前用户基本信息。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@RegisterReflectionForBinding
public record LoginResult(
        /** Access Token（短期有效，用于接口访问） */
        String accessToken,
        /** Access Token 过期时间（毫秒时间戳） */
        long accessTokenExpireAt,
        /** Token 类型，固定 Bearer */
        String tokenType,
        /** 当前登录用户信息 */
        CurrentUser user
) {

    public LoginResult {
        if (tokenType == null) tokenType = "Bearer";
    }

    /**
     * 快捷构建
     */
    public static LoginResult of(String accessToken, long accessTokenExpireMs, CurrentUser user) {
        long now = System.currentTimeMillis();
        return new LoginResult(
                accessToken,
                now + accessTokenExpireMs,
                "Bearer", user
        );
    }
}
