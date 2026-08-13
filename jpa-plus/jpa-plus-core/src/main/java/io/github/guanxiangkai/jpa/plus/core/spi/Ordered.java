package io.github.guanxiangkai.jpa.plus.core.spi;

/**
 * 统一排序契约接口
 *
 * <p>所有需要参与 SPI 排序的实现类均应实现此接口。
 * {@link JpaPlusLoader} 加载后按 {@link #order()} 升序排列（值越小越优先）。</p>
 *
 * <p>设计意图：消除 {@code JpaPlusLoader} 内部的 {@code instanceof} 分支，
 * 遵循开闭原则 —— 新增 SPI 类型时无需修改加载器排序逻辑。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface Ordered {

    /**
     * 最低优先级（最后执行）
     */
    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;

    /**
     * 最高优先级（最先执行）
     */
    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;

    /**
     * 排序值，值越小越优先
     *
     * @return 排序优先级，默认 {@link #LOWEST_PRECEDENCE}（最低优先级）
     */
    default int order() {
        return LOWEST_PRECEDENCE;
    }
}

