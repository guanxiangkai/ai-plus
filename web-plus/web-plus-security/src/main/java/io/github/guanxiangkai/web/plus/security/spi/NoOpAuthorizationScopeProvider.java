package io.github.guanxiangkai.web.plus.security.spi;

/**
 * 默认授权范围加载器：不提供任何角色、权限或部门数据范围。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class NoOpAuthorizationScopeProvider implements AuthorizationScopeProvider {

    @Override
    public AuthorizationScope load(String userId, boolean superAdmin) {
        return AuthorizationScope.EMPTY;
    }
}
