package io.github.guanxiangkai.jpa.plus.field.autofill.annotation;

import java.lang.annotation.*;

/**
 * 创建人自动填充注解
 *
 * <p>标注在字段上，首次保存时自动填充当前操作人。
 * 仅在字段值为 {@code null} 时填充，已有值不会被覆盖（确保更新时不变）。</p>
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
 *     @CreateBy
 *     private String createBy;
 *
 *     @CreateBy
 *     private Long creatorId;   // 也支持数值类型
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
public @interface CreateBy {
}

