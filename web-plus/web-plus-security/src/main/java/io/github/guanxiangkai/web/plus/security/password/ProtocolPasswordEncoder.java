package io.github.guanxiangkai.web.plus.security.password;

import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 显式选择客户端摘要协议的 BCrypt 密码编码器。
 *
 * <p>输入遵循 {@link PasswordProtocol}，存储仍是标准裸 BCrypt。实际编码、随机盐和匹配
 * 全部委托 Spring Security；不重新摘要输入，不推断或迁移已有密码。
 * 本类不会自动注册 Bean，也不会改变基础库默认 PasswordEncoder 的行为。</p>
 */
public final class ProtocolPasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    /**
     * 校验摘要密码后进行 BCrypt 编码。
     *
     * @param password 客户端摘要密码
     * @return 标准裸 BCrypt 存储值
     * @throws IllegalArgumentException 输入不符合协议时抛出
     */
    @Override
    public String encode(@Nullable CharSequence password) {
        return bcrypt.encode(PasswordProtocol.requirePassword(password));
    }

    /**
     * 校验输入协议和存储格式，再委托 BCrypt 匹配。
     *
     * @param password 客户端摘要密码
     * @param encodedPassword 标准裸 BCrypt 存储值
     * @return 格式错误或密码不匹配时为 false
     */
    @Override
    public boolean matches(@Nullable CharSequence password, @Nullable String encodedPassword) {
        String value;
        try {
            value = PasswordProtocol.requirePassword(password);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (!PasswordProtocol.isBcryptHash(encodedPassword)) return false;
        return bcrypt.matches(value, encodedPassword);
    }

    /**
     * 判断存储格式或 BCrypt 成本是否需要由调用方重新编码。
     *
     * @param encodedPassword 已编码的密码
     * @return 非法格式或成本不足时为 true
     */
    @Override
    public boolean upgradeEncoding(@Nullable String encodedPassword) {
        return !PasswordProtocol.isBcryptHash(encodedPassword) || bcrypt.upgradeEncoding(encodedPassword);
    }
}
