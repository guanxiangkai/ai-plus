package io.github.guanxiangkai.web.plus.security.spi;

/**
 * 默认授权范围缓存失效器。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class NoOpAuthorizationScopeCache implements AuthorizationScopeCache {

    @Override
    public void evictUser(String userId) {
    }

    @Override
    public void clearAll() {
    }
}
