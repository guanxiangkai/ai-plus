package io.github.guanxiangkai.web.plus.security.spi;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * 响应式当前用户信息提供者（WebFlux 默认实现）
 *
 * <h3>读取优先级</h3>
 * <ol>
 *   <li><b>Reactor Context</b>（key: {@link CurrentUserHolder#REACTOR_CONTEXT_KEY}）——
 *       由 {@link io.github.guanxiangkai.web.plus.security.filter.TokenAuthenticationFilter} 写入，
 *       响应式链路首选，零阻塞。</li>
 *   <li><b>Spring Security {@link ReactiveSecurityContextHolder}</b> ——
 *       兼容未使用 {@code TokenAuthenticationFilter} 但启用 Spring Security 的场景；
 *       要求 {@code Authentication.getDetails()} 返回 {@link CurrentUser} 实例。</li>
 *   <li><b>ThreadLocal {@link CurrentUserHolder}</b> ——
 *       同步上下文兜底；Micrometer Context Propagation 已将 Reactor Context 还原到
 *       ThreadLocal 时也可命中。</li>
 * </ol>
 *
 * <p>业务项目可实现 {@link CurrentUserProvider} 并注册为 Spring Bean 来覆盖此默认实现。</p>
 *
 * @author guanxiangkai
 * @see CurrentUserProvider
 * @see CurrentUserHolder#REACTOR_CONTEXT_KEY
 * @since 1.0.0
 */
public class ReactiveCurrentUserProvider implements CurrentUserProvider {

    /**
     * 同步获取：仅读 ThreadLocal（适用于已由 Micrometer 还原上下文的场景）。
     */
    @Override
    public Optional<CurrentUser> getCurrentUser() {
        return Optional.ofNullable(CurrentUserHolder.get());
    }

    /**
     * 响应式获取：优先从 Reactor Context 读取，其次 Spring Security，最后 ThreadLocal。
     */
    @Override
    public Mono<Optional<CurrentUser>> getCurrentUserMono() {
        return Mono.deferContextual(ctxView -> {
            // 1. Reactor Context —— TokenAuthenticationFilter 写入的规范来源
            if (ctxView.hasKey(CurrentUserHolder.REACTOR_CONTEXT_KEY)) {
                CurrentUser user = ctxView.get(CurrentUserHolder.REACTOR_CONTEXT_KEY);
                return Mono.just(Optional.ofNullable(user));
            }
            // 2. Spring Security ReactiveSecurityContextHolder —— 兼容非 JWT 认证机制
            return ReactiveSecurityContextHolder.getContext()
                    .map(sc -> {
                        Authentication auth = sc.getAuthentication();
                        if (auth != null && auth.isAuthenticated()
                                && auth.getDetails() instanceof CurrentUser user) {
                            return Optional.of(user);
                        }
                        return Optional.<CurrentUser>empty();
                    })
                    // 3. ThreadLocal 兜底（Micrometer 传播后可命中）
                    .defaultIfEmpty(Optional.ofNullable(CurrentUserHolder.get()));
        });
    }
}
