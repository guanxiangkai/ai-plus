package io.github.guanxiangkai.web.plus.core.spi;

import io.github.guanxiangkai.web.plus.core.model.DictItem;

import java.util.List;

/**
 * 字典数据写入端点 SPI
 * <p>
 * {@link DictWriter} 通过此契约向框架管理的字典存储写入完整的字典类型数据，
 * 不依赖具体缓存或持久化实现。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 * @see DictWriter
 */
@FunctionalInterface
public interface DictWriteSink {

    /**
     * 写入（覆盖）指定类型的字典数据。
     * <p>
     * 存储实现以本次数据作为该类型的权威内容。
     * </p>
     *
     * @param code  字典类型标识，如 {@code "sys_status"}
     * @param items 字典项列表，value 与 label 均不可为 null
     */
    void put(String code, List<DictItem> items);
}
