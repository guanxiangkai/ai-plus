package io.github.guanxiangkai.web.plus.license;

/**
 * 许可证签发、载入或校验失败。
 *
 * <p>异常消息仅描述失败类别，绝不包含令牌或私钥内容。</p>
 */
public final class LicenseException extends RuntimeException {

    public LicenseException(String message) {
        super(message);
    }

}
