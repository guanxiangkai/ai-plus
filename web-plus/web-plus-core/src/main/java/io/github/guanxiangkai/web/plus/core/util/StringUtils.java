package io.github.guanxiangkai.web.plus.core.util;

/**
 * 字符串工具类
 * <p>
 * GraalVM JDK 25 优化
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class StringUtils {

    private StringUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 判断字符串是否为空
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 判断字符串是否为空白
     */
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * 判断字符串是否不为空白
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 默认值
     */
    public static String defaultIfEmpty(String str, String defaultStr) {
        return isEmpty(str) ? defaultStr : str;
    }

    /**
     * 默认空字符串
     */
    public static String defaultString(String str) {
        return str == null ? "" : str;
    }

    /**
     * 截取字符串（安全）
     */
    public static String substring(String str, int start, int end) {
        if (str == null) {
            return null;
        }
        if (start < 0) {
            start = 0;
        }
        if (end > str.length()) {
            end = str.length();
        }
        if (start > end) {
            return "";
        }
        return str.substring(start, end);
    }

    /**
     * 手机号脱敏：隐藏中间四位（13812345678 -> 138****5678）。
     */
    public static String maskMobile(String phone) {
        if (isBlank(phone)) {
            return phone;
        }
        String digits = phone.trim();
        if (digits.length() != 11) {
            return phone;
        }
        return digits.substring(0, 3) + "****" + digits.substring(7);
    }
}

