package io.github.guanxiangkai.jpa.plus.field.sensitive.exception;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;
import io.github.guanxiangkai.jpa.plus.field.sensitive.annotation.SensitiveWordStrategy;

/**
 * 敏感词异常
 *
 * <p>当 {@link SensitiveWordStrategy#REJECT}
 * 策略检测到敏感词时抛出。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class SensitiveWordException extends JpaPlusException {

    public SensitiveWordException(String message) {
        super(message);
    }
}

