package io.github.guanxiangkai.redis.plus.cache.serializer;

import io.github.guanxiangkai.redis.plus.core.exception.RedisPlusException;
import io.github.guanxiangkai.redis.plus.core.serializer.ValueSerializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Jackson 的统一序列化器
 *
 * <p>实现 core 模块通用 {@link ValueSerializer}，作为缓存、幂等、队列等模块的统一 JSON 序列化器。
 * 使用显式 envelope 将类型信息与业务 payload 分离，避免启用 Jackson DefaultTyping。
 * 由 {@code RedisPlusCacheAutoConfiguration} 自动注册为默认 Bean。
 *
 * <p><b>安全说明</b>：反序列化只会读取显式目标类型；仅当目标类型为 {@link Object}
 * 时才会使用 envelope 内的类型名，并且必须命中内置包或配置白名单。
 */
@SuppressWarnings("NullAway")
public class JacksonValueSerializer implements ValueSerializer {

    private static final String TYPE_FIELD = "@type";
    private static final String PAYLOAD_FIELD = "payload";

    private final ObjectMapper objectMapper;
    private final List<String> additionalPackages;

    public JacksonValueSerializer(ObjectMapper objectMapper) {
        this(objectMapper, Collections.emptyList());
    }

    /**
     * @param objectMapper       Jackson 3 ObjectMapper（复用调用方已完成的全局配置）
     * @param additionalPackages 额外允许反序列化的包名前缀，用于扩展类型白名单
     */
    public JacksonValueSerializer(ObjectMapper objectMapper, List<String> additionalPackages) {
        this.additionalPackages = Collections.unmodifiableList(additionalPackages);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public String serialize(Object value) {
        Objects.requireNonNull(value, "value must not be null");
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put(TYPE_FIELD, value.getClass().getName());
            envelope.set(PAYLOAD_FIELD, objectMapper.valueToTree(value));
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RedisPlusException("缓存值序列化失败：" + value.getClass().getName(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(String data, Class<T> type) {
        if (data == null || data.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(data);
            if (isEnvelope(root)) {
                Class<?> targetType = type == Object.class ? resolveEnvelopeType(root) : type;
                Object value = objectMapper.treeToValue(root.get(PAYLOAD_FIELD), targetType);
                return (T) value;
            }
            return objectMapper.treeToValue(root, type);
        } catch (Exception e) {
            throw new RedisPlusException("缓存值反序列化失败，targetType=" + type.getName(), e);
        }
    }

    private boolean isEnvelope(JsonNode root) {
        return root != null && root.isObject() && root.hasNonNull(TYPE_FIELD) && root.has(PAYLOAD_FIELD);
    }

    private Class<?> resolveEnvelopeType(JsonNode root) throws ClassNotFoundException {
        String typeName = root.get(TYPE_FIELD).asString();
        if (!isAllowedType(typeName)) {
            throw new RedisPlusException("缓存值类型不在反序列化白名单内，type=" + typeName);
        }
        return Class.forName(typeName);
    }

    private boolean isAllowedType(String typeName) {
        return typeName.startsWith("io.github.guanxiangkai.redis.plus.")
                || typeName.startsWith("java.util.")
                || typeName.startsWith("java.lang.")
                || typeName.startsWith("java.time.")
                || typeName.startsWith("java.math.")
                || additionalPackages.stream().anyMatch(typeName::startsWith);
    }
}
