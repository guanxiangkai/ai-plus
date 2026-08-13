package io.github.guanxiangkai.web.plus.dict;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

/**
 * 字典 Redis 变更监听器
 * <p>
 * 监听 {@code dict:refresh} 频道的 Redis Pub/Sub 消息。当其他实例通过
 * {@link DictRefresher} 刷新字典后发布通知，本实例收到消息后自动失效本地 L1 缓存，
 * 下次读取时触发 L3（{@link io.github.guanxiangkai.web.plus.core.spi.DictProvider}）重新加载。
 * </p>
 *
 * <h3>消息协议</h3>
 * <ul>
 *   <li>频道：{@value #CHANNEL}</li>
 *   <li>消息体：字典类型编码（如 {@code "sys_status"}），或 {@code "*"} 表示全量刷新</li>
 * </ul>
 *
 * @author guanxiangkai
 * @since 1.0.0
 * @see DictRefresher
 * @see RedisDictStore
 */
@Slf4j
public class DictChangeListener implements MessageListener {

    /** Redis Pub/Sub 频道名 */
    public static final String CHANNEL = "dict:refresh";

    /** 全量刷新标记 */
    public static final String ALL_MARKER = "*";

    private final RedisDictStore dictStore;

    public DictChangeListener(RedisDictStore dictStore) {
        this.dictStore = dictStore;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("[web-plus] 收到字典变更通知: {}", body);

        if (ALL_MARKER.equals(body)) {
            dictStore.invalidateAll();
        } else {
            dictStore.invalidate(body);
        }
    }
}
