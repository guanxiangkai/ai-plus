package io.github.guanxiangkai.jpa.plus.audit.snapshot;

/**
 * 审计快照字段值序列化策略（SPI）
 *
 * <p>控制 {@link io.github.guanxiangkai.jpa.plus.audit.interceptor.SnapshotAuditInterceptor}
 * 在采集字段值时如何将 Java 对象转换为可存储/传输的格式。</p>
 *
 * <h3>典型使用场景</h3>
 * <ul>
 *   <li>将 {@code LocalDateTime}、枚举等复杂类型序列化为 JSON 字符串，便于持久化到审计日志表</li>
 *   <li>对敏感字段值做额外脱敏处理（注意：敏感字段建议直接使用 {@code @AuditExclude} 跳过）</li>
 *   <li>使用 MessagePack、Protobuf 等二进制格式提高存储效率</li>
 * </ul>
 *
 * <h3>框架内置实现</h3>
 * <ul>
 *   <li>{@link #NOOP} —— 直接返回原始值（默认），{@code FieldDiff.before()/after()} 保持 Java 对象类型</li>
 *   <li>{@link JacksonSnapshotSerializer} —— 使用 Jackson 将复杂类型序列化为 JSON 字符串（可选）</li>
 * </ul>
 *
 * <h3>注册方式</h3>
 * <pre>{@code
 * @Bean
 * public SnapshotAuditInterceptor snapshotAuditInterceptor(
 *         AuditEventPublisher publisher, EntityManager em, ObjectMapper objectMapper) {
 *     return new SnapshotAuditInterceptor(publisher, em,
 *             new JacksonSnapshotSerializer(objectMapper));
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.jpa.plus.audit.interceptor.SnapshotAuditInterceptor
 * @see JacksonSnapshotSerializer
 * @since 1.0.0
 */
@FunctionalInterface
public interface SnapshotSerializer {

    /**
     * 无操作序列化器（默认）
     *
     * <p>直接返回原始 Java 对象，{@link AuditSnapshot} 中的字段值保持原始类型。
     * 适合事件消费方能够处理 Java 对象的场景（如同进程内内存审计）。</p>
     */
    SnapshotSerializer NOOP = (fieldName, value) -> value;

    // ─── 内置默认实现 ───────────────────────────────────────────────────────

    /**
     * 序列化一个字段值
     *
     * @param fieldName 字段名（可用于按字段名实现不同策略）
     * @param value     字段的原始值，可能为 {@code null}
     * @return 序列化后的值（可与原始值类型不同）；若返回原始 value 则表示无需转换
     */
    Object serializeValue(String fieldName, Object value);
}

