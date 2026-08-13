package io.github.guanxiangkai.web.plus.security.spi;

/**
 * Token 吊销存储 SPI
 * <p>
 * 负责存储和查询已吊销的 Token 或用户的当前有效 Token 版本（单点登录踢出场景）。
 * 默认为无状态 no-op 实现；若启用 {@code redis-plus} 则可注册 Redis 实现。
 * </p>
 *
 * <h3>扩展方式</h3>
 * <pre>{@code
 * @Bean
 * public TokenRevocationStore tokenRevocationStore(RedisTemplate<String, String> redis) {
 *     return new RedisTokenRevocationStore(redis);
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface TokenRevocationStore {

    /**
     * 标记 Token 已被吊销（加入黑名单）
     *
     * @param token    Token 字符串
     * @param ttlMs    黑名单保留时长（毫秒），建议设为 Token 剩余有效期
     */
    void revoke(String token, long ttlMs);

    /**
     * 检查 Token 是否在黑名单中
     *
     * @param token Token 字符串
     * @return {@code true} 表示已被吊销
     */
    boolean isRevoked(String token);

    /**
     * 记录用户当前的有效 Token（单点登录：新登录踢出旧 Token）
     *
     * @param userId  用户 ID
     * @param token   最新 Token 字符串
     * @param ttlMs   有效期（毫秒）
     */
    default void setCurrentToken(String userId, String token, long ttlMs) {
    }

    /**
     * 获取用户当前的有效 Token
     *
     * @param userId 用户 ID
     * @return 当前有效 Token，若未设置则返回 {@code null}
     */
    default String getCurrentToken(String userId) {
        return null;
    }

    /**
     * 吊销指定用户的所有 Token（清除当前 Token 记录）
     *
     * @param userId 用户 ID
     */
    default void revokeAll(String userId) {
    }
}
