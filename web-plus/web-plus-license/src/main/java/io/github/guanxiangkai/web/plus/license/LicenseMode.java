package io.github.guanxiangkai.web.plus.license;

/**
 * 许可证的校验方式。
 */
public enum LicenseMode {
    /** 可在不联网时使用的许可证。 */
    OFFLINE,
    /** 必须与当前在线请求随机数绑定的短期许可证。 */
    ONLINE
}
