package io.github.guanxiangkai.jpa.plus.field.autofill.spi;

/**
 * 当前操作人提供者（SPI）
 *
 * <p>框架通过此接口获取当前登录用户信息，用于 {@code @CreateBy} / {@code @UpdateBy} 字段自动填充。
 * 用户只需实现此接口并注册为 Spring Bean，框架自动注入。</p>
 *
 * <h3>实现示例</h3>
 *
 * <p><b>1. Spring Security 集成：</b></p>
 * <pre>{@code
 * @Component
 * public class SecurityUserProvider implements CurrentUserProvider {
 *     @Override
 *     public Object getCurrentUser() {
 *         var auth = SecurityContextHolder.getContext().getAuthentication();
 *         return auth != null ? auth.getName() : "system";
 *     }
 * }
 * }</pre>
 *
 * <p><b>2. 自定义上下文：</b></p>
 * <pre>{@code
 * @Component
 * public class MyUserProvider implements CurrentUserProvider {
 *     @Override
 *     public Object getCurrentUser() {
 *         return UserContext.getCurrentUserId();  // 返回 Long 用户 ID
 *     }
 * }
 * }</pre>
 *
 * <p><b>注意：</b>如果未注册此 Bean，{@code @CreateBy} / {@code @UpdateBy} 注解将被忽略
 * （不会抛异常，仅打印 DEBUG 日志），其余自动填充功能不受影响。</p>
 *
 * <p><b>设计模式：</b>策略模式（Strategy） —— 解耦用户身份获取与框架自动填充逻辑</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface CurrentUserProvider {

    /**
     * 获取当前操作人标识
     *
     * <p>返回值类型应与 {@code @CreateBy} / {@code @UpdateBy} 标注的字段类型兼容。
     * 常见返回类型：{@code String}（用户名）、{@code Long}（用户 ID）。</p>
     *
     * @return 当前操作人标识，返回 {@code null} 时不填充
     */
    Object getCurrentUser();
}

