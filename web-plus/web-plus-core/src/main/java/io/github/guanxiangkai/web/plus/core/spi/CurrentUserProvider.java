package io.github.guanxiangkai.web.plus.core.spi;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * 当前用户信息提供者 SPI
 * <p>
 * 业务侧可实现此接口，为 {@code web-plus} 各模块提供当前登录用户信息。
 * 默认实现（{@code ReactiveCurrentUserProvider}）在响应式链路中依次尝试
 * Reactor Context → Spring Security ReactiveSecurityContextHolder → ThreadLocal。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface CurrentUserProvider {

    /**
     * 同步获取当前登录用户，若未认证返回 {@link Optional#empty()}。
     * <p>适用于同步（MVC / 虚拟线程）上下文，或 Micrometer 已将 Reactor Context 还原到 ThreadLocal 之后。</p>
     */
    Optional<CurrentUser> getCurrentUser();

    /**
     * 响应式获取当前登录用户，适用于 WebFlux 响应式链路。
     * <p>
     * 默认实现将 {@link #getCurrentUser()} 包装进 {@link Mono#fromCallable}，
     * 可在已完成 ThreadLocal 传播的订阅上下文中正确读取。
     * </p>
     * <p>
     * 响应式场景<strong>强烈建议</strong>覆盖此方法，通过
     * {@link reactor.util.context.ContextView} 或
     * {@code ReactiveSecurityContextHolder}
     * 读取用户，彻底避免对 ThreadLocal 的隐式依赖。
     * </p>
     */
    default Mono<Optional<CurrentUser>> getCurrentUserMono() {
        return Mono.fromCallable(this::getCurrentUser);
    }

    /**
     * 获取当前用户 ID，快捷方法
     */
    default String getCurrentUserId() {
        return getCurrentUser().map(CurrentUser::userId).orElse(null);
    }

    /**
     * 判断当前是否已认证
     */
    default boolean isAuthenticated() {
        return getCurrentUser().isPresent();
    }
}

