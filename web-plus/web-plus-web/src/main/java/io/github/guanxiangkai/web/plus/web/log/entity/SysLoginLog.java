package io.github.guanxiangkai.web.plus.web.log.entity;

import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;

/**
 * 登录日志传输实体
 * <p>
 * 作为 Auth 服务 → Redis Stream → System 服务 的消息载体：
 * <ol>
 *   <li>Auth 通过 {@code @LoginLog(entity = SysLoginLog.class)} 触发 {@code LoginLogAspect} 创建并填充本对象</li>
 *   <li>{@code LoginLogHandlerImpl}（security 模块）将其序列化并写入 Redis Stream {@code log:login:stream}</li>
 *   <li>System 服务消费后映射到业务侧 {@code LoginLog} JPA 实体落库</li>
 * </ol>
 * 本类仅作为 MQ 消息 DTO，不含 JPA 注解。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysLoginLog extends BaseLog {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 登录动作（LOGIN / LOGOUT / REFRESH_TOKEN）
     * <p>来源：{@code @LoginLog#action()}</p>
     */
    private String loginAction;

    /**
     * 客户端 User-Agent 原始值
     * <p>来源：{@code LoginLogAspect} 自动注入</p>
     */
    private String userAgent;

    /**
     * 解析后的浏览器名称（如 Chrome 124）
     * <p>可在 {@code LoginLogHandlerImpl} 中通过 UA 解析库填充</p>
     */
    private String browser;

    /**
     * 解析后的操作系统（如 Windows 11、macOS Sequoia）
     * <p>可在 {@code LoginLogHandlerImpl} 中通过 UA 解析库填充</p>
     */
    private String os;
}
