package io.github.guanxiangkai.jpa.plus.query.ast;

import java.util.List;

/**
 * 条件组合工具类
 *
 * <p>提供条件合并的便捷方法，避免在多个拦截器中重复编写条件组合逻辑。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class Conditions {

    private Conditions() {
    }

    /**
     * 将额外条件与已有条件进行 AND 合并
     *
     * @param existing   已有条件（可以为 {@code null}）
     * @param additional 要追加的条件
     * @return 合并后的条件
     */
    public static Condition and(Condition existing, Condition additional) {
        if (additional == null) return existing;
        if (existing == null) return additional;
        return new And(List.of(existing, additional));
    }

    /**
     * 将额外条件与已有条件进行 OR 合并
     *
     * @param existing   已有条件（可以为 {@code null}）
     * @param additional 要追加的条件
     * @return 合并后的条件
     */
    public static Condition or(Condition existing, Condition additional) {
        if (additional == null) return existing;
        if (existing == null) return additional;
        return new Or(List.of(existing, additional));
    }
}

