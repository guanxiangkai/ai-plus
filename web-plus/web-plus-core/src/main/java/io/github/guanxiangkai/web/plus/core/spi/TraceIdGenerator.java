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
     * 生成全局唯一 TraceId
     */
    String generate();
}

