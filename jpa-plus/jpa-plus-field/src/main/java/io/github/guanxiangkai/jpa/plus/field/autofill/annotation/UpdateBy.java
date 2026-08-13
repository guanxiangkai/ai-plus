package io.github.guanxiangkai.jpa.plus.field.autofill.annotation;

import java.lang.annotation.*;

/**
 * 更新人自动填充注解
 *
 * <p>标注在字段上，每次保存时自动填充当前操作人（覆盖已有值）。
 * 首次保存和后续更新均会触发填充。</p>
 *
 * <p>当前操作人由用户实现的
 * {@link io.github.guanxiangkai.jpa.plus.field.autofill.spi.CurrentUserProvider} 提供。</p>
 *
 * <h3>支持的字段类型</h3>
 * <ul>
 *   <li>{@link String}（用户名 / 工号）</li>
 *   <li>{@link Long} / {@code long}（用户 ID）</li>
 *   <li>{@link Integer} / {@code int}（用户 ID）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Entity
 * public class User {
 *     @UpdateBy
 *     private String updateBy;
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.jpa.plus.field.autofill.spi.CurrentUserProvider
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UpdateBy {
}

