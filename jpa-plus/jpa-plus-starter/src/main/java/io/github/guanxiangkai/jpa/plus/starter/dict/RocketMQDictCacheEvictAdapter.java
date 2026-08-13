package io.github.guanxiangkai.jpa.plus.starter.dict;

import org.springframework.context.ApplicationEventPublisher;

/**
 * RocketMQ 消费端字典缓存广播失效适配器模板
 *
 * <p>功能与 {@link KafkaDictCacheEvictAdapter} 相同，使用 RocketMQ 作为消息总线。</p>
 *
 * <h3>使用方式</h3>
 * <ol>
 *   <li>引入 {@code rocketmq-spring-boot-starter} 依赖：
 *   <pre>{@code
 *   implementation("org.apache.rocketmq:rocketmq-spring-boot-starter:2.3.0")
 *   }</pre>
 *   </li>
 *   <li>复制此类到业务项目，取消注释 {@code @RocketMQMessageListener} 注解：
 *   <pre>{@code
 *   @Component
 *   @RocketMQMessageListener(
 *       topic     = "${jpa-plus.dict.evict-topic:dict-change-topic}",
 *       consumerGroup = "${spring.application.name:app}-dict-evict"
 *   )
 *   public class DictCacheEvictRocketConsumer extends RocketMQDictCacheEvictAdapter
 *           implements RocketMQListener<String> {
 *       public DictCacheEvictRocketConsumer(ApplicationEventPublisher pub) { super(pub); }
 *
 *       @Override
 *       public void onMessage(String dictCode) { handleEvict(dictCode); }
 *   }
 *   }</pre>
 *   </li>
 * </ol>
 *
 * <h3>发布端示例</h3>
 * <pre>{@code
 * @Autowired RocketMQTemplate rocketMQTemplate;
 *
 * rocketMQTemplate.convertAndSend("dict-change-topic", dictCode);  // 失效指定字典
 * rocketMQTemplate.convertAndSend("dict-change-topic", "");        // 失效全部
 * }</pre>
 *
 * @author guanxiangkai
 * @see DictCacheEvictEvent
 * @see KafkaDictCacheEvictAdapter
 * @since 1.0.0
 */
// ─── 取消以下注释并添加 rocketmq-spring-boot-starter 依赖即可激活 ───────────
// @Component
public class RocketMQDictCacheEvictAdapter {

    private final ApplicationEventPublisher eventPublisher;

    public RocketMQDictCacheEvictAdapter(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 消费字典变更消息，发布本地失效事件
     *
     * <p>消息格式约定与 {@link KafkaDictCacheEvictAdapter#handleEvict(String)} 相同。</p>
     *
     * <pre>{@code
     * // ── 实现 RocketMQListener 时调用此方法 ──
     * @Override
     * public void onMessage(String dictCode) {
     *     handleEvict(dictCode);
     * }
     * }</pre>
     */
    public void handleEvict(String dictCode) {
        DictCacheEvictEvent event = (dictCode == null || dictCode.isBlank() || "*".equals(dictCode))
                ? DictCacheEvictEvent.evictAll(this)
                : DictCacheEvictEvent.of(this, dictCode);
        eventPublisher.publishEvent(event);
    }
}

