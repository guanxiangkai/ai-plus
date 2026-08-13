package io.github.guanxiangkai.web.plus.core.annotation;

import java.lang.annotation.*;

/**
 * 注入当前登录用户 ID 到方法参数。
 * <p>
 * 配合 Web 层注册的 {@code HandlerMethodArgumentResolver} 使用，可直接在 Controller 方法参数上标注：
 * <pre>
 *     &#64;GetMapping("/profile")
 *     public ApiResponse&lt;UserVO&gt; profile(&#64;CurrentUserId String userId) { ... }
 * </pre>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUserId {
}
