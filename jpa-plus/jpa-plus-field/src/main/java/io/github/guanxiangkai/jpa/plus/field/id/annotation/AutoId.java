package io.github.guanxiangkai.jpa.plus.field.id.annotation;

import io.github.guanxiangkai.jpa.plus.field.id.enums.IdType;

import java.lang.annotation.*;

/**
 * 自动 ID 生成注解
 *
 * <p>标注在实体的主键字段上，保存时若 ID 为 null，框架自动生成并填充。
 * 配合 {@link IdType} 指定生成策略，未指定时跟随全局配置
 * {@code jpa-plus.id-generator.type}。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Entity
 * public class Order {
 *
 *     @Id
 *     @AutoId // 跟随全局配置（默认雪花算法）
 *     private Long id;
 *
 *     @AutoId(IdType.UUID) // 显式指定 UUID 策略
 *     private String traceId;
 * }
 * }</pre>
 *
 * <h3>支持的字段类型</h3>
 * <ul>
 *   <li>{@link IdType#SNOWFLAKE} → {@code Long / long / String}</li>
 *   <li>{@link IdType#UUID} → {@code String}</li>
 *   <li>{@link IdType#CUSTOM} → 由 {@code IdGenerator} SPI 实现决定</li>
 * </ul>
 *
 * <p><b>注意：</b>仅在字段值为 {@code null} 时生成，已有值的字段不会被覆盖。</p>
 *
 * @author guanxiangkai
 * @see IdType
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoId {

    /**
     * ID 生成策略
     *
     * <p>默认 {@link IdType#AUTO}，即跟随全局配置
     * {@code jpa-plus.id-generator.type}。
     * 显式指定时覆盖全局配置。</p>
     *
     * @return ID 生成策略
     */
    IdType value() default IdType.AUTO;
}

