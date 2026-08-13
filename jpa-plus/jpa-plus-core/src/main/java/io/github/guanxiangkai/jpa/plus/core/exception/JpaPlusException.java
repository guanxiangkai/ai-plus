package io.github.guanxiangkai.jpa.plus.core.exception;

/**
 * JPA-Plus 框架统一异常基类
 *
 * <p>所有框架内部异常均继承此类，便于统一捕获与处理。
 * 作为 {@link RuntimeException} 的子类，不强制调用方捕获。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class JpaPlusException extends RuntimeException {

    /**
     * 构造仅含消息的异常
     *
     * @param message 异常描述信息
     */
    public JpaPlusException(String message) {
        super(message);
    }

    /**
     * 构造包含消息与根因的异常
     *
     * @param message 异常描述信息
     * @param cause   根因异常
     */
    public JpaPlusException(String message, Throwable cause) {
        super(message, cause);
    }
}
