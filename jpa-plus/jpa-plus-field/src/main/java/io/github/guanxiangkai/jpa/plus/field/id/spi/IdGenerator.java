package io.github.guanxiangkai.jpa.plus.field.id.spi;

import java.lang.reflect.Field;

/**
 * 自定义 ID 生成器接口（SPI）
 *
 * <p>当 {@code @AutoId(IdType.CUSTOM)} 或全局配置 {@code jpa-plus.id-generator.type=CUSTOM} 时，
 * 框架委托此接口生成主键值。用户只需实现此接口并注册为 Spring Bean 即可。</p>
 *
 * <h3>实现示例</h3>
 * <pre>{@code
 * @Component
 * public class MyIdGenerator implements IdGenerator {
 *
 *     @Override
 *     public Object generate(Field field) {
 *         // 根据字段类型或注解自定义生成逻辑
 *         if (field.getType() == Long.class) {
 *             return myDistributedIdService.nextId();
 *         }
 *         return UUID.randomUUID().toString();
 *     }
 * }
 * }</pre>
 *
 * <p><b>设计模式：</b>策略模式（Strategy） —— 解耦 ID 生成逻辑与框架</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface IdGenerator {

    /**
     * 生成主键值
     *
     * <p>框架保证仅在字段值为 {@code null} 时调用此方法。
     * 返回值类型应与字段类型兼容（如 Long 字段返回 Long，String 字段返回 String）。</p>
     *
     * @param field 标注了 {@code @AutoId} 的实体字段（可读取字段类型、注解等元信息）
     * @return 生成的主键值（不可为 null）
     */
    Object generate(Field field);
}

