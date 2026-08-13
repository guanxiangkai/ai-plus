package io.github.guanxiangkai.web.plus.error.exception;

import io.github.guanxiangkai.web.plus.error.enums.WebErrorCode;

/**
 * 认证异常 —— 未登录、Token 失效等
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class AuthException extends WebPlusException {

    public AuthException(WebErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(String message) {
        super(WebErrorCode.UNAUTHORIZED.getCode(), message, 401);
    }

    public static AuthException unauthorized() {
        return new AuthException(WebErrorCode.UNAUTHORIZED);
    }

    public static AuthException tokenInvalid() {
        return new AuthException(WebErrorCode.TOKEN_INVALID);
    }

    public static AuthException tokenExpired() {
        return new AuthException(WebErrorCode.TOKEN_EXPIRED);
    }
}

