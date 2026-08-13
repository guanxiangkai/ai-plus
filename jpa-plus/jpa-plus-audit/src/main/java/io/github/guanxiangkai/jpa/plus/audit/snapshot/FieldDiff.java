package io.github.guanxiangkai.jpa.plus.audit.snapshot;

/**
 * 字段变更差异（不可变值对象）
 *
 * <p>包含字段名、变更前的值以及变更后的值。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public record FieldDiff(
        String fieldName,
        Object before,
        Object after
) {

    /**
     * 是否为新增字段（before 为 null，after 不为 null）
     */
    public boolean isAdded() {
        return before == null && after != null;
    }

    /**
     * 是否为删除字段（before 不为 null，after 为 null）
     */
    public boolean isRemoved() {
        return before != null && after == null;
    }

    /**
     * 是否为修改字段
     */
    public boolean isModified() {
        return before != null && after != null && !before.equals(after);
    }
}

