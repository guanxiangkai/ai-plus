package io.github.guanxiangkai.web.plus.security.service;

/**
 * JWT 服务接口
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface IJwtService {
    /**
     * 从令牌获取用户 ID
     *
     * @param token 令牌
     * @return 用户ID
     */
    String getUserIdFromToken(String token);
}
