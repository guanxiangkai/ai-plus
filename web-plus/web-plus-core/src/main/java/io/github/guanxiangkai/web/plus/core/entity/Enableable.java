package io.github.guanxiangkai.web.plus.core.entity;

/**
 * 启用/禁用能力接口
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface Enableable {

    Boolean getEnabled();

    /**
     * 是否已启用
     */
    default boolean isEnabled() {
        return Boolean.TRUE.equals(getEnabled());
    }

    void setEnabled(Boolean enabled);
}

