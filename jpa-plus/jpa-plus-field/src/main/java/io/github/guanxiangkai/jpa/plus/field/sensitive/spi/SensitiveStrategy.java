package io.github.guanxiangkai.jpa.plus.field.sensitive.spi;

import io.github.guanxiangkai.jpa.plus.field.sensitive.dfa.SensitiveWordEngine;

/**
 * 敏感词处理策略接口（可扩展枚举模式）
 *
 * <p>内置实现见 {@link io.github.guanxiangkai.jpa.plus.field.sensitive.annotation.SensitiveWordStrategy SensitiveWordStrategy}。
 * 用户可自定义枚举实现此接口，支持审计、标记、部分替换等任意处理方式。</p>
 *
 * <h3>扩展示例</h3>
 * <pre>{@code
 * public enum MySensitiveStrategy implements SensitiveStrategy {
 *     AUDIT {
 *         @Override
 *         public String handle(String text, DfaEngine engine, String replacement) {
 *             List<String> hits = engine.findAll(text);
 *             if (!hits.isEmpty()) {
 *                 auditLogger.log("敏感词命中: " + hits);
 *             }
 *             return text; // 放行但记录
 *         }
 *     },
 *     PARTIAL_REPLACE {
 *         @Override
 *         public String handle(String text, DfaEngine engine, String replacement) {
 *             // 只替换首个敏感词，其余放行
 *             List<String> hits = engine.findAll(text);
 *             if (!hits.isEmpty()) {
 *                 return text.replace(hits.getFirst(), replacement);
 *             }
 *             return text;
 *         }
 *     };
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface SensitiveStrategy {

    /**
     * 处理含敏感词的文本
     *
     * @param text        原始文本
     * @param engine      敏感词检测引擎（内置 DfaEngine 或 houbb HoubbSensitiveWordEngine，自动选择）
     * @param replacement 替换字符串（来自 {@code @SensitiveWord#replacement()}）
     * @return 处理后的文本（REJECT 策略可抛异常阻止保存）
     */
    String handle(String text, SensitiveWordEngine engine, String replacement);
}
