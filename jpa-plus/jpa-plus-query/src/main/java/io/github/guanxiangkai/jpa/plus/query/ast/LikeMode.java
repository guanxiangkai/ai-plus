package io.github.guanxiangkai.jpa.plus.query.ast;

/**
 * LIKE 匹配模式
 */
public enum LikeMode {
    /**
     * 前缀匹配：value%
     */
    START,
    /**
     * 后缀匹配：%value
     */
    END,
    /**
     * 任意位置匹配：%value%
     */
    ANYWHERE
}

