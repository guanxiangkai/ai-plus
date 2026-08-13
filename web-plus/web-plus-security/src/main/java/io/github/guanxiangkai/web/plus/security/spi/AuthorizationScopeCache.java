package io.github.guanxiangkai.web.plus.security.spi;

/**
 * 授权范围缓存失效 SPI。
 * <p>
 * 业务系统在菜单、角色、用户授权变更后，可注入该接口主动清理授权范围缓存。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface AuthorizationScopeCache {

    /**
     * 清理指定用户的授权范围缓存。
     *
     * @param userId 用户 ID
     */
    void evictUser(String userId);

    /**
     * 清理全部授权范围缓存。
     */
    void clearAll();
}
