package io.github.guanxiangkai.jpa.plus.sharding.exception;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;

/**
 * 分片路由异常
 *
 * <p>当跨分片查询失败、找不到分片规则、或分片路由计算错误时抛出。</p>
 */
public final class ShardingRouteException extends JpaPlusException {

    public ShardingRouteException(String message) {
        super(message);
    }

    public ShardingRouteException(String message, Throwable cause) {
        super(message, cause);
    }
}
