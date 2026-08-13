package io.github.guanxiangkai.web.plus.security.service;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;

import java.util.Map;
import java.util.Optional;

/**
 * Token 服务 SPI —— 负责 Token 签发、解析与校验
 * <p>
 * 默认实现为 {@code JwtTokenService}（无状态 JWT）。
 * 若需要 Redis Session 模式，业务侧注册自定义 Bean 即可替换。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface TokenService {

    /**
     * 签发 Access Token
     *
     * @param user 当前用户信息
     * @return Access Token 字符串
     */
    String createAccessToken(CurrentUser user);

    /**
     * 解析 Token，返回 Claims
     *
     * @param token Token 字符串
     * @return Claims Map，Token 无效时返回空
     */
    Optional<Map<String, Object>> parseToken(String token);

    /**
     * 从 Token 中解析出当前用户对象
     *
     * @param token Token 字符串
     * @return 用户信息，Token 无效时返回空
     */
    Optional<CurrentUser> parseCurrentUser(String token);

    /**
     * 吊销（黑名单化）Token
     *
     * @param token Token 字符串
     */
    default void revokeToken(String token) {
    }

    /**
     * 检查 Token 是否已被吊销
     */
    default boolean isRevoked(String token) {
        return false;
    }

    /**
     * 吊销指定用户的所有 Token（单点登录踢出场景）
     *
     * @param userId 用户 ID
     */
    default void revokeAllUserTokens(String userId) {
    }
}
