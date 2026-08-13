package io.github.guanxiangkai.web.plus.core.model;

import java.io.Serializable;
import java.util.HashMap;

/**
 * 选项项模型（用于下拉框等场景）
 *
 * @param label 选项显示文本
 * @param value 选项提交值
 * @param extra 选项额外信息
 * @author guanxiangkai
 * @since 1.0.0
 */
public record OptionItem(String label, String value, HashMap<String, String> extra) implements Serializable {

    public static OptionItem of(String label, String value, HashMap<String, String> extra) {
        return new OptionItem(label, value, extra);
    }

    public static OptionItem of(String label, String value) {
        return new OptionItem(label, value, null);
    }
}
