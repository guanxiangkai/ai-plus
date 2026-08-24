package io.github.guanxiangkai.web.plus.core.spi;

/**
 * 链路 ID 生成器 SPI
 * <p>
 * 默认实现生成 UUID，业务侧可替换为雪花算法、SkyWalking TraceId 等。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface TraceIdGenerator {

    /**
     * 返回当前标准追踪上下文中的 TraceId。
     *
     * <p>没有活动追踪上下文时返回 {@code null}，调用方随后可复用可信上游请求头或生成新值。</p>
     *
     * @return 当前 TraceId；不存在时返回 {@code null}
     */
    default String currentTraceId() {
        return null;
    }

    /**
     * 生成全局唯一 TraceId
     */
    String generate();
}
