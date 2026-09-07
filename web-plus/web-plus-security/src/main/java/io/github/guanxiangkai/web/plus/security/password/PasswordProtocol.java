package io.github.guanxiangkai.web.plus.security.password;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 客户端摘要密码的输入约束。
 *
 * <p>适用于已约定提交 UTF-8 原始密码 SHA-1 小写十六进制摘要的应用。
 * 对外字段仍可命名为 {@code password}、{@code oldPassword} 和 {@code newPassword}；
 * 字段名称与内容处理是独立契约。摘要是密码等效凭据，不能代替 HTTPS 或日志脱敏。</p>
 */
public final class PasswordProtocol {

    /** 客户端摘要密码的格式，可用于 Bean Validation 的 Pattern 约束。 */
    public static final String PASSWORD_PATTERN = "^[0-9a-f]{40}$";

    private static final String EMPTY_PASSWORD = sha1Utf8("");
    private static final Pattern PASSWORD = Pattern.compile(PASSWORD_PATTERN);
    private static final Pattern BCRYPT = Pattern.compile(
            "^\\$2[aby]\\$(?:0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}$");

    private PasswordProtocol() {
    }

    /**
     * 校验并返回密码输入，不执行二次摘要或空白归一化。
     *
     * @param password 客户端提交的摘要密码
     * @return 已通过格式和非空密码约束的原值
     * @throws IllegalArgumentException 输入为空、格式不符或对应空密码时抛出
     */
    public static String requirePassword(@Nullable CharSequence password) {
        if (password == null || !PASSWORD.matcher(password).matches()
                || EMPTY_PASSWORD.contentEquals(password)) {
            throw new IllegalArgumentException("密码必须为非空原始密码的40位小写SHA-1十六进制摘要");
        }
        return password.toString();
    }

    /**
     * 判断存储值是否符合标准裸 BCrypt 格式及合法成本范围。
     *
     * @param encodedPassword 已编码的密码，允许为空
     * @return 是否为成本 04 至 31 的 2a、2b 或 2y BCrypt 值
     */
    public static boolean isBcryptHash(@Nullable String encodedPassword) {
        return encodedPassword != null && BCRYPT.matcher(encodedPassword).matches();
    }

    /**
     * 将原始密码按 UTF-8 转成协议摘要；已收到摘要的服务端不得再次调用此方法。
     *
     * @param rawPassword 原始密码
     * @return 40 位小写 SHA-1 十六进制摘要
     * @throws IllegalArgumentException 原始密码为 null 时抛出
     */
    public static String sha1Utf8(@Nullable CharSequence rawPassword) {
        if (rawPassword == null) throw new IllegalArgumentException("密码不能为空");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(rawPassword.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持SHA-1", exception);
        }
    }
}
