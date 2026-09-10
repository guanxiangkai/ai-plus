package io.github.guanxiangkai.web.plus.core.tree;

import org.jspecify.annotations.Nullable;

/**
 * 具有父级归属关系的对象。
 *
 * @param <ID> 身份标识类型
 */
public interface ParentAware<ID> {

    /**
     * 获取父级身份标识。
     *
     * @return 父级身份标识；根节点通常返回 {@code null}，实际根判定由调用方提供
     */
    @Nullable ID getParentId();
}
