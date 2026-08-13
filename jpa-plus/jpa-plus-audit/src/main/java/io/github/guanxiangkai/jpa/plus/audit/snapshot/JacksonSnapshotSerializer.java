package io.github.guanxiangkai.jpa.plus.audit.snapshot;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * 基于 Jackson 的审计快照序列化器
 *
 * <p>将字段值序列化为 JSON 字符串，适合需要将 {@link AuditSnapshot} 字段差异
 * 持久化到数据库 VARCHAR/TEXT 列或通过消息队列传输的场景。</p>
 *
 * <h3>序列化规则</h3>
 * <ul>
 *   <li>{@code null} → 保持 {@code null}（不序列化）</li>
 *   <li>{@code String} / 基本类型包装类（{@code Number}、{@code Boolean}、{@code Character}）
 *       → 直接返回原始值（无需额外序列化）</li>
 *   <li>其他类型（{@code LocalDateTime}、枚举、自定义对象等）→ 序列化为 JSON 字符串</li>
 *   <li>序列化失败时退回 {@code toString()}，并输出 WARN 日志</li>
 * </ul>
 *
 * <h3>注册方式</h3>
 * <pre>{@code
 * // 注册为 Spring Bean
 * public SnapshotAuditInterceptor snapshotAuditInterceptor(
 *         AuditEventPublisher publisher, EntityManager em, ObjectMapper objectMapper) {
 *     // 使用 Jackson 序列化快照字段值
 *     return new SnapshotAuditInterceptor(publisher, em,
 *             new JacksonSnapshotSerializer(objectMapper));
 * }
 * }</pre>
 *
 * <h3>依赖要求</h3>
 * <p>需要 {@code tools.jackson.core:jackson-databind} 在 classpath 中（
 * Spring Boot 项目通常已包含）。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class JacksonSnapshotSerializer implements SnapshotSerializer {

    private final ObjectMapper objectMapper;

    /**
     * 使用指定的 {@link ObjectMapper} 构造序列化器
     *
     * @param objectMapper Jackson ObjectMapper（建议复用 Spring 容器中的单例）
     */
    public JacksonSnapshotSerializer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    }

    /**
     * 使用默认 {@link ObjectMapper}（无特殊配置，不推荐在 Spring 应用中使用）
     */
    public JacksonSnapshotSerializer() {
        this(new ObjectMapper());
    }

    @Override
    public Object serializeValue(String fieldName, Object value) {
        if (value == null) {
            return null;
        }
        // 基本类型和 String 无需序列化，直接返回
        if (value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character) {
            return value;
        }
        // 其他类型序列化为 JSON 字符串
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            log.warn("[jpa-plus] SnapshotSerializer: failed to serialize field '{}' (type={}), fallback to toString()",
                    fieldName, value.getClass().getSimpleName(), e);
            return value.toString();
        }
    }
}
