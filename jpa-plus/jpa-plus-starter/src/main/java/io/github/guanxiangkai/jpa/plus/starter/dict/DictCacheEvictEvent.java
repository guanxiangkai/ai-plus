package io.github.guanxiangkai.jpa.plus.starter.dict;

import org.springframework.context.ApplicationEvent;

import java.util.Optional;

/**
 * 字典缓存主动失效事件（Spring 事件）
 *
 * <p>业务方通过 {@link org.springframework.context.ApplicationEventPublisher#publishEvent(Object)}
 * 发布此事件，{@link CachedDictProvider} 监听到后立即清除对应缓存条目，
 * 下次查询时重新从 {@link io.github.guanxiangkai.jpa.plus.field.dict.spi.DictProvider} 获取最新数据。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 失效指定字典编码的缓存
 * applicationEventPublisher.publishEvent(DictCacheEvictEvent.of(this, "sys_user_sex"));
 *
 * // 失效全部字典缓存
 * applicationEventPublisher.publishEvent(DictCacheEvictEvent.evictAll(this));
 * }</pre>
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>不引入任何外部依赖（Redis PubSub 等），仅使用 Spring 应用事件机制</li>
 *   <li>适合单节点或以配置中心推送为触发源的场景</li>
 *   <li>多节点场景需配合消息队列广播事件（如 Kafka/RocketMQ 消费后本地再发布该事件）</li>
 * </ul>
 *
 * @author guanxiangkai
 * @see CachedDictProvider
 * @since 1.0.0
 */
public class DictCacheEvictEvent extends ApplicationEvent {

    /**
     * 要失效的字典编码；{@code null} 表示清除全部缓存
     */
    private final String dictCode;

    /**
     * 创建指定字典编码失效事件
     *
     * @param source   事件来源（通常传 {@code this}）
     * @param dictCode 要失效的字典编码，{@code null} 时等同于 {@link #evictAll(Object)}
     */
    public DictCacheEvictEvent(Object source, String dictCode) {
        super(source);
        this.dictCode = dictCode;
    }

    /**
     * 构建指定字典编码失效事件（静态工厂方法）
     */
    public static DictCacheEvictEvent of(Object source, String dictCode) {
        return new DictCacheEvictEvent(source, dictCode);
    }

    /**
     * 构建全量失效事件（清除所有字典缓存）
     */
    public static DictCacheEvictEvent evictAll(Object source) {
        return new DictCacheEvictEvent(source, null);
    }

    /**
     * 获取要失效的字典编码
     *
     * @return {@link Optional#empty()} 表示清除全部缓存
     */
    public Optional<String> getDictCode() {
        return Optional.ofNullable(dictCode);
    }
}

