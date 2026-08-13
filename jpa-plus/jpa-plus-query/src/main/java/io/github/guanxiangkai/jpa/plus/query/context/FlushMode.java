package io.github.guanxiangkai.jpa.plus.query.context;

/**
 * Flush 策略枚举
 */
public enum FlushMode {
    /**
     * 自动模式（默认）：仅当查询需要保证看到之前修改时，由框架自动触发 flush
     */
    AUTO,
    /**
     * 始终模式：每次查询前都执行 flush
     */
    ALWAYS,
    /**
     * 从不模式：完全不主动 flush，完全依赖 JPA 默认行为
     */
    NEVER
}

