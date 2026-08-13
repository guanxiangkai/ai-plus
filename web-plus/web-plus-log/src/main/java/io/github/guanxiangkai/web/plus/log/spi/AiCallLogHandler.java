package io.github.guanxiangkai.web.plus.log.spi;

import io.github.guanxiangkai.web.plus.log.entity.BaseLog;

/**
 * AI 模型调用日志持久化策略 SPI
 *
 * <h3>字段名约定（实体字段名与此一致时由切面自动填充）</h3>
 * <pre>
 * 公共（BaseLog setter）: traceId, userId, username, tenantId, status, message, logTime
 * 专属（反射写入）       : operationId, provider, model, description,
 *                         inputContent, outputContent, costMs, errorMessage
 * 注意：promptTokens/completionTokens/totalTokens 由业务方在 handle() 中从 AI SDK 响应提取后手动设置
 * </pre>
 *
 * <p>具体项目应将该事件映射到自身当前的调用审计模型，不在通用能力中约定物理表名。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface AiCallLogHandler {
    void handle(BaseLog entity);
}
