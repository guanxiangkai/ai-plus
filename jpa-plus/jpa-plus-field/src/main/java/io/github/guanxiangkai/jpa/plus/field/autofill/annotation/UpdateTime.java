package io.github.guanxiangkai.jpa.plus.field.autofill.annotation;

import java.lang.annotation.*;

/**
 * 更新时间自动填充注解
 *
 * <p>标注在时间类型字段上，每次保存时自动填充当前时间（覆盖已有值）。
 * 首次保存和后续更新均会触发填充。</p>
 *
 * <h3>支持的字段类型</h3>
 * <ul>
 *   <li>{@link java.time.LocalDateTime}（推荐）</li>
 *   <li>{@link java.time.LocalDate}</li>
 *   <li>{@link java.time.LocalTime}</li>
 *   <li>{@link java.time.OffsetDateTime}</li>
 *   <li>{@link java.time.ZonedDateTime}</li>
 *   <li>{@link java.time.Instant}</li>
 *   <li>{@link java.util.Date}</li>
 *   <li>{@link java.sql.Timestamp}</li>
 *   <li>{@link java.sql.Date}</li>
 *   <li>{@link Long} / {@code long}（毫秒时间戳）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Entity
 * public class User {
 *     @UpdateTime
 *     private LocalDateTime updateTime;
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UpdateTime {
}

