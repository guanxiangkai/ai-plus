package io.github.guanxiangkai.jpa.plus.field.sensitive.dfa;

import java.util.List;

/**
 * 敏感词检测引擎接口
 *
 * <p>框架提供两种实现：</p>
 * <ul>
 *   <li>{@link DfaEngine}           —— 内置轻量 DFA，无外部依赖，作为零依赖兜底</li>
 *   <li>{@link HoubbSensitiveWordEngine} —— 基于 {@code com.github.houbb:sensitive-word} 的高级引擎，
 *       支持全角/半角、繁简转换、拼音绕过、数字替换检测等，<b>自动优先使用</b>（当 houbb 在 classpath 时）</li>
 * </ul>
 *
 * <p>用户无需关心使用的是哪种引擎，框架根据 classpath 自动选择。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface SensitiveWordEngine {

    /**
     * 检测文本中是否包含任意敏感词（找到即返回，性能最优）
     *
     * @param text 待检测文本
     * @return 包含敏感词返回 {@code true}
     */
    boolean contains(String text);

    /**
     * 将文本中所有敏感词替换为指定字符串
     *
     * @param text        原始文本
     * @param replacement 替换字符串（如 {@code "***"}）
     * @return 替换后的文本，无敏感词时原样返回
     */
    String replace(String text, String replacement);

    /**
     * 查找文本中所有被命中的敏感词（去重）
     *
     * @param text 待检测文本
     * @return 命中的敏感词列表（不含重复项）
     */
    List<String> findAll(String text);

    /**
     * 词库中词条数量
     */
    int wordCount();

    /**
     * 词库是否为空（无任何有效敏感词）
     */
    default boolean isEmpty() {
        return wordCount() == 0;
    }
}

