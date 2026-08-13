package io.github.guanxiangkai.jpa.plus.starter.dict;

import org.springframework.context.ApplicationEventPublisher;

/**
 * Kafka 消费端字典缓存广播失效适配器模板
 *
 * <p>当字典数据在任意节点发生变更时，通过 Kafka Topic 广播失效消息，
 * 集群内各节点消费后发布 {@link DictCacheEvictEvent}，触发各自的 {@link CachedDictProvider}
 * 立即清除本地缓存，实现集群级字典缓存一致性。</p>
 *
 * <h3>使用方式</h3>
 * <ol>
 *   <li>引入 {@code spring-kafka} 依赖：
 *   <pre>{@code
 *   implementation("org.springframework.kafka:spring-kafka")
 *   }</pre>
 *   </li>
 *   <li>复制此类到业务项目，取消注释相关注解，按需调整 Topic 和消息格式：
 *   <pre>{@code
 *   @Component
 *   @EnableKafka
 *   public class DictCacheEvictKafkaConsumer extends KafkaDictCacheEvictAdapter { ... }
 *   }</pre>
 *   </li>
 *   <li>在字典数据变更时，向对应 Topic 发送消息即可触发集群级失效。</li>
 * </ol>
 *
 * <h3>发布端示例</h3>
 * <pre>{@code
 * // 字典管理端在保存字典后发送 Kafka 消息
 * @Autowired KafkaTemplate<String, String> kafkaTemplate;
 *
 * kafkaTemplate.send("dict-change-topic", dictCode);         // 失效指定字典
 * kafkaTemplate.send("dict-change-topic", "");               // 空字符串 = 失效全部
 * }</pre>
 *
 * @author guanxiangkai
 * @see DictCacheEvictEvent
 * @see CachedDictProvider
 * @since 1.0.0
 */
// ─── 取消以下注释并添加 spring-kafka 依赖即可激活 ───────────────────────────
// @Component
public class KafkaDictCacheEvictAdapter {

    private final ApplicationEventPublisher eventPublisher;

    public KafkaDictCacheEvictAdapter(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 消费字典变更消息，发布本地失效事件
     *
     * <p>消息格式约定：
     * <ul>
     *   <li>消息内容为字典编码（如 {@code "sys_user_sex"}）→ 失效指定字典</li>
     *   <li>消息内容为空字符串或 {@code "*"} → 失效全部缓存</li>
     * </ul>
     * </p>
     *
     * <pre>{@code
     * // ── 开启 Kafka 监听时替换为以下实现 ──
     * @KafkaListener(topics = "${jpa-plus.dict.evict-topic:dict-change-topic}",
     *                groupId = "${spring.application.name:app}-dict-evict")
     * public void onDictChangeMessage(
     *         @Payload String dictCode,
     *         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
     *     log.info("[jpa-plus] Received dict evict message from Kafka topic={}, dictCode={}", topic, dictCode);
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

