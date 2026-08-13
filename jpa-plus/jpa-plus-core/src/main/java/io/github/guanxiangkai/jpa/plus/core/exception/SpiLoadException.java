package io.github.guanxiangkai.jpa.plus.core.exception;

/**
 * SPI 加载异常
 *
 * <p>当通过 JDK {@code ServiceLoader} 加载 SPI 实现时发生错误，抛出此异常。</p>
 */
public final class SpiLoadException extends JpaPlusException {

    public SpiLoadException(String message) {
        super(message);
    }

    public SpiLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
