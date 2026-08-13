package io.github.guanxiangkai.jpa.plus.field.sensitive.annotation;

import io.github.guanxiangkai.jpa.plus.field.sensitive.dfa.SensitiveWordEngine;
import io.github.guanxiangkai.jpa.plus.field.sensitive.exception.SensitiveWordException;
import io.github.guanxiangkai.jpa.plus.field.sensitive.spi.SensitiveStrategy;

/**
 * 内置敏感词处理策略枚举
 *
 * <p>实现 {@link SensitiveStrategy} 接口，用户可自定义枚举实现该接口以扩展处理方式。</p>
 *
 * @author guanxiangkai
 * @see SensitiveStrategy
 * @since 1.0.0
 */
public enum SensitiveWordStrategy implements SensitiveStrategy {

    /**
     * 拒绝：包含敏感词时抛出 {@link SensitiveWordException}，阻止保存，并在异常消息中列出命中词
     */
    REJECT {
        @Override
        public String handle(String text, SensitiveWordEngine engine, String replacement) {
            if (engine.contains(text)) {
                throw new SensitiveWordException("文本包含敏感词: " + engine.findAll(text));
            }
            return text;
        }
    },

    /**
     * 替换：将文本中所有敏感词替换为 {@code replacement} 后保存（最长匹配）
     */
    REPLACE {
        @Override
        public String handle(String text, SensitiveWordEngine engine, String replacement) {
            return engine.replace(text, replacement);
        }
    }
}
