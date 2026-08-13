package io.github.guanxiangkai.web.plus.core.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * 字典项
 * <p>
 * 表示一条字典数据，包含存储值（value）和展示标签（label）。
 * 通常由业务服务从数据库查询后封装，通过 {@link io.github.guanxiangkai.web.plus.core.spi.DictWriteSink}
 * 写入 Redis 供 web-plus 读取翻译。
 * </p>
 *
 * <h3>示例</h3>
 * <pre>{@code
 * List<DictItem> statusItems = List.of(
 *     new DictItem("0", "禁用"),
 *     new DictItem("1", "启用")
 * );
 * sink.put("sys_status", statusItems);
 * }</pre>
 *
 * @param value 字典存储值（如 {@code "1"}）
 * @param label 字典展示标签（如 {@code "启用"}）
 * @author guanxiangkai
 * @since 1.0.0
 */
public record DictItem(String value, String label) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 紧凑构造校验：value 与 label 均不可为 null
     */
    public DictItem {
        if (value == null) throw new IllegalArgumentException("DictItem.value 不可为 null");
        if (label == null) throw new IllegalArgumentException("DictItem.label 不可为 null");
    }
}

