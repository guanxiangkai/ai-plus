package io.github.guanxiangkai.web.plus.security.spi;

/**
 * 用户授权范围加载 SPI。
 * <p>
 * JWT 与网关透传仅承载身份字段，业务系统可实现该接口从 Redis、数据库等来源加载
 * 角色、权限、部门数据范围。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface AuthorizationScopeProvider {

    /**
     * 加载用户授权范围。
     *
     * @param userId     用户 ID
     * @param superAdmin 是否超级管理员
     * @return 授权范围；无授权范围时返回 {@link AuthorizationScope#EMPTY}
     */
    AuthorizationScope load(String userId, boolean superAdmin);
}
