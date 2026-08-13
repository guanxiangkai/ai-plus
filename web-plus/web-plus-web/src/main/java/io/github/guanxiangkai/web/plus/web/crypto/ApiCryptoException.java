package io.github.guanxiangkai.web.plus.web.crypto;

/**
 * API 加解密异常。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class ApiCryptoException extends RuntimeException {

    public ApiCryptoException(String message) {
        super(message);
    }

    public ApiCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
