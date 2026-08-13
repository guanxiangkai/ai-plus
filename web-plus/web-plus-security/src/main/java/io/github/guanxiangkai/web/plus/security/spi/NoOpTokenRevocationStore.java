package io.github.guanxiangkai.web.plus.security.spi;

/**
 * Token 吊销存储的无状态 no-op 实现（默认）
 * <p>
 * 当项目未配置 Redis 时使用此实现，所有操作均为空操作。
 * 若需要真正的 Token 吊销能力，注册一个 {@link TokenRevocationStore} Bean（如基于 Redis 的实现）。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class NoOpTokenRevocationStore implements TokenRevocationStore {

    @Override
    public void revoke(String token, long ttlMs) {
    }

    @Override
    public boolean isRevoked(String token) {
        return false;
    }
}
