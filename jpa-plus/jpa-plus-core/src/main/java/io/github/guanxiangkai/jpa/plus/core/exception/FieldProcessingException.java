package io.github.guanxiangkai.jpa.plus.core.exception;

/**
 * 字段处理异常
 *
 * <p>当 {@link io.github.guanxiangkai.jpa.plus.core.field.FieldHandler} 在处理实体字段时
 * （自动填充、加密解密、字典翻译等）发生错误，抛出此异常。</p>
 *
 * <p>包含字段名和实体类名信息，便于快速定位问题。</p>
 */
public final class FieldProcessingException extends JpaPlusException {

    public FieldProcessingException(String message) {
        super(message);
    }

    public FieldProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
