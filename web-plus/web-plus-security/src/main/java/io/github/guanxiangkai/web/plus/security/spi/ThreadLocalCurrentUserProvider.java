package io.github.guanxiangkai.web.plus.security.spi;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;

import java.util.Optional;

/**
 * 基于 {@link CurrentUserHolder} ThreadLocal 的默认用户信息提供者
 *
 * <p>
 * 适用于显式选择 ThreadLocal 上下文的同步适配场景。响应式服务默认使用
 * {@link ReactiveCurrentUserProvider}，业务项目也可以注册自定义 {@link CurrentUserProvider} Bean。
 * </p>
 *
 * <h3>典型覆盖场景</h3>
 * <ul>
 *   <li>需要从响应式 SecurityContext 读取用户（纯 Spring Security 模式，无 ThreadLocal）</li>
 *   <li>需要从数据库实时加载用户扩展属性</li>
 *   <li>多租户场景下需要增强用户上下文信息</li>
 * </ul>
 *
 * @author guanxiangkai
 * @see CurrentUserProvider
 * @see CurrentUserHolder
 * @since 1.0.0
 */
public class ThreadLocalCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<CurrentUser> getCurrentUser() {
        return Optional.ofNullable(CurrentUserHolder.get());
    }
}
