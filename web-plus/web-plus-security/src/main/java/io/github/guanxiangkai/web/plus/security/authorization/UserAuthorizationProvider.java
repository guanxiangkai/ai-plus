package io.github.guanxiangkai.web.plus.security.authorization;

/**
 * 从服务端存储加载用户授权范围，避免将权限列表放入 token 或请求头。
 */
public interface UserAuthorizationProvider {

    AuthorizationScope load(String userId, boolean superAdmin);
}
