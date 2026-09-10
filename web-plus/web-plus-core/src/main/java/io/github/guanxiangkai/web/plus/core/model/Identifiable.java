package io.github.guanxiangkai.web.plus.core.model;

import org.jspecify.annotations.Nullable;

/**
 * 具有可读取身份标识的对象。
 *
 * @param <ID> 身份标识类型；对象尚未持久化时实现可以返回 {@code null}
 */
public interface Identifiable<ID> {

    /**
     * 获取对象的身份标识。
     *
     * @return 身份标识；尚未生成标识时可以为 {@code null}
     */
    @Nullable ID getId();
}
