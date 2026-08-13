package io.github.guanxiangkai.jpa.plus.field.dict.model;

import java.io.Serializable;
import java.util.Map;

/**
 * 字典项数据模型（不可变值对象）
 *
 * <p>表示一条字典项，由 {@link io.github.guanxiangkai.jpa.plus.field.dict.spi.DictProvider DictProvider}
 * 提供给翻译逻辑使用。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 基本用法
 * DictTranslateItem item = new DictTranslateItem("sys_user_sex", "1", "男", "primary", 1);
 * item.label();  // "男"
 *
 * // 带扩展字段
 * DictTranslateItem item = new DictTranslateItem("sys_color", "red", "红色", "danger", 1,
 *     Map.of("hex", "#FF0000", "rgb", "255,0,0"));
 * item.extra().get("hex");  // "#FF0000"
 * }</pre>
 *
 * @param dictCode 字典类型编码
 * @param value    字典值
 * @param label    字典标签（用于前端展示）
 * @param style    字典项类型（如 primary / success / warning 等 Tag 颜色）
 * @param sort     排序号
 * @param extra    扩展属性（业务自定义的键值对，如颜色值、图标、跳转链接等）
 * @author guanxiangkai
 * @since 1.0.0
 */
public record DictTranslateItem(
        String dictCode,
        String value,
        String label,
        String style,
        Integer sort,
        Map<String, String> extra
) implements Serializable {

    /**
     * 简化构造（无扩展字段）
     */
    public DictTranslateItem(String dictCode, String value, String label, String style, Integer sort) {
        this(dictCode, value, label, style, sort, Map.of());
    }

    /**
     * 防御性拷贝：确保 extra 不可变
     */
    public DictTranslateItem {
        extra = extra != null ? Map.copyOf(extra) : Map.of();
    }
}

