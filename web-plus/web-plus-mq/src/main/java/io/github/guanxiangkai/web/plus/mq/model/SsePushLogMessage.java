package io.github.guanxiangkai.web.plus.mq.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SSE 推送日志 MQ 消息模型
 * <p>
 * SSE 模块在推送完成后将此消息发布到 {@code log.sse-push} 主题，
 * system 模块的 {@code ssePushLogConsumer} 消费后持久化到 {@code sys_log} 表（logType = SSE_SEND）。
 * </p>
 *
 * @param messageId   MQ 消息 ID
 * @param targetType  推送目标类型（USER / USERS / TENANT / BROADCAST）
 * @param messageType 消息类型（字典：sse_message_type）
 * @param userId      目标用户 ID（单用户推送）
 * @param userIds     目标用户 ID 列表（多用户推送，逗号分隔或 JSON）
 * @param content     推送内容（JSON）
 * @param pushStatus  推送状态（pending / success / skipped / failed）
 * @param failReason  失败/跳过原因
 * @param retryCount  重试次数
 * @param tenantId    租户 ID
 * @param pushTime    推送时间
 * @author guanxiangkai
 * @since 1.0.0
 */
public record SsePushLogMessage(
        String messageId,
        String targetType,
        String messageType,
        String userId,
        String userIds,
        String content,
        String pushStatus,
        String failReason,
        Integer retryCount,
        String tenantId,
        LocalDateTime pushTime
) implements Serializable {

    /**
     * 推送主题常量
     */
    public static final String TOPIC = "log.sse-push";
    @Serial
    private static final long serialVersionUID = 1L;
}

