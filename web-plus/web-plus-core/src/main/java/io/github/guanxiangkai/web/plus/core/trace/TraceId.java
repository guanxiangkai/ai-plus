package io.github.guanxiangkai.web.plus.core.trace;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * TraceId 协议值校验与消息头转换。
 *
 * <p>只允许字母、数字、下划线和连字符，限制为 1 至 64 个字符，避免日志注入和响应头分割。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class TraceId {

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9_\\-]{1,64}");

    private TraceId() {
    }

    /**
     * 判断候选值是否符合 TraceId 协议约束。
     *
     * @param value 候选值
     * @return 符合约束时返回 {@code true}
     */
    public static boolean isValid(String value) {
        return value != null && SAFE_VALUE.matcher(value).matches();
    }

    /**
     * 将 HTTP 或消息系统中的头值转换为经过校验的 TraceId。
     *
     * @param value 字符串或 UTF-8 字节数组头值
     * @return 合法 TraceId；值不存在或非法时返回 {@code null}
     */
    public static String fromHeader(Object value) {
        String candidate = null;
        if (value instanceof CharSequence sequence) {
            candidate = sequence.toString();
        } else if (value instanceof byte[] bytes) {
            candidate = new String(bytes, StandardCharsets.UTF_8);
        }
        return isValid(candidate) ? candidate : null;
    }
}
