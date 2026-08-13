package io.github.guanxiangkai.web.plus.dict.autoconfigure;

import io.github.guanxiangkai.redis.plus.cache.ThreeLevelCacheTemplate;
import io.github.guanxiangkai.web.plus.core.spi.DictProvider;
import io.github.guanxiangkai.web.plus.core.spi.DictWriter;
import io.github.guanxiangkai.web.plus.dict.DictChangeListener;
import io.github.guanxiangkai.web.plus.dict.DictRefresher;
import io.github.guanxiangkai.web.plus.dict.DictTranslator;
import io.github.guanxiangkai.web.plus.dict.RedisDictStore;
import io.github.guanxiangkai.web.plus.dict.properties.DictProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Collections;
import java.util.List;

/**
 * 字典回显自动配置
 * <p>
 * 在满足以下条件时自动装配字典回显能力：
 * </p>
 * <ul>
 *   <li>当前应用为 WebFlux 响应式 Web 应用</li>
 *   <li>classpath 中存在 {@link ThreeLevelCacheTemplate}（引入了 redis-plus-starter）</li>
 *   <li>容器中注册 {@link ThreeLevelCacheTemplate} Bean</li>
 *   <li>{@code web-plus.dict.enabled} 为 {@code true}（默认）</li>
 * </ul>
 *
 * <h3>三级缓存分层</h3>
 * <pre>
 * L1 — JVM 本地 Caffeine（热点字典无 Redis 开销，TTL 由 redis-plus.cache.local.ttl 控制）
 * L2 — Redis 分布式缓存（存完整 value→label Map，TTL 由 web-plus.dict.ttl 控制）
 * L3 — {@link DictProvider}（缓存未命中时提供字典数据）
 * </pre>
 *
 * <h3>yaml 示例</h3>
 * <pre>
 * web-plus:
 *   dict:
 *     enabled: true       # 是否启用字典回显（默认 true）
 *     key-prefix: dict    # 保留字段（文档用途，实际 key 由 ThreeLevelCacheTemplate 管理）
 *     ttl: 24h            # L2 Redis 缓存 TTL（默认 24h）
 *     fail-fast: false    # 初始化失败是否中断启动（默认 false）
 *
 * redis-plus:
 *   cache:
 *     local:
 *       ttl: 5m           # L1 本地缓存 TTL（默认 5m）
 *       maximum-size: 10000
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(ThreeLevelCacheTemplate.class)
@ConditionalOnBean(ThreeLevelCacheTemplate.class)
@ConditionalOnProperty(prefix = "web-plus.dict", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DictProperties.class)
public class DictAutoConfiguration {

    public DictAutoConfiguration() {
        log.info("[web-plus] 📖 字典回显模块已启用（redis-plus 三级缓存：L1 Caffeine + L2 Redis）");
    }

    /**
     * 注册字典存储 Bean（三级缓存版：L1 + L2 + L3）。
     * <p>每个字典类型的完整 Map 缓存在 L1，同一请求内多字段翻译无 Redis 开销。</p>
     */
    @Bean
    @ConditionalOnMissingBean(RedisDictStore.class)
    public RedisDictStore redisDictStore(ThreeLevelCacheTemplate threeLevelCacheTemplate,
                                         DictProperties dictProperties,
                                         ObjectProvider<DictProvider> dictProviderProvider) {
        DictProvider provider = dictProviderProvider.getIfAvailable(() -> code -> Collections.emptyList());
        return new RedisDictStore(threeLevelCacheTemplate, dictProperties, provider);
    }

    /**
     * 注册字典翻译器 Bean。
     * <p>业务侧注入后调用 {@code translate(vo)} / {@code translateList(list)} 完成字段回写。</p>
     */
    @Bean
    @ConditionalOnMissingBean(DictTranslator.class)
    public DictTranslator dictTranslator(RedisDictStore redisDictStore) {
        return new DictTranslator(redisDictStore);
    }

    /**
     * 注册字典刷新器 Bean。
     * <p>
     * 使用 {@link ObjectProvider} 收集所有 {@link DictWriter} Bean（零个时不报错），
     * 业务侧 {@code @Component} 实现 {@code DictWriter} 即可自动加入刷新列表。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(DictRefresher.class)
    public DictRefresher dictRefresher(ObjectProvider<DictWriter> writersProvider,
                                        RedisDictStore redisDictStore,
                                        DictProperties dictProperties,
                                        StringRedisTemplate stringRedisTemplate) {
        List<DictWriter> writers = writersProvider.orderedStream().toList();
        log.debug("[web-plus] 检测到 {} 个 DictWriter 实现: {}",
                writers.size(),
                writers.stream().map(w -> w.getClass().getSimpleName()).toList());
        return new DictRefresher(writers, redisDictStore, dictProperties, stringRedisTemplate);
    }

    /**
     * 应用就绪后初始化字典缓存（触发所有 DictWriter 向三级缓存写入数据）。
     */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> dictInitializer(DictRefresher dictRefresher) {
        return event -> {
            log.info("[web-plus] 应用就绪，开始初始化字典缓存（L1+L2）…");
            dictRefresher.refresh();
        };
    }

    /**
     * 注册字典变更监听器，监听 Redis Pub/Sub {@code dict:refresh} 频道。
     * <p>当其他实例刷新字典后，本实例自动失效 L1 缓存，下次读取时触发 L3 回源。</p>
     */
    @Bean
    @ConditionalOnMissingBean(DictChangeListener.class)
    public DictChangeListener dictChangeListener(RedisDictStore redisDictStore) {
        return new DictChangeListener(redisDictStore);
    }

    /**
     * 注册 Redis 消息监听容器，订阅 {@code dict:refresh} 频道。
     */
    @Bean
    public RedisMessageListenerContainer dictRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            DictChangeListener dictChangeListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(dictChangeListener, new ChannelTopic(DictChangeListener.CHANNEL));
        return container;
    }
}
