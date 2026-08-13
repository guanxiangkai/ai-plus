package io.github.guanxiangkai.web.plus.log.entity;

import io.github.guanxiangkai.web.plus.core.entity.DataTenantEntity;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 日志实体公共基类（Template Method 模式 — 抽象父类）
 *
 * <p>
 * 所有日志类型的 JPA MappedSuperclass 顶层基类，定义所有日志共有的字段。
 * 本类不映射任何 {@code @Table}，由业务项目继承并加上
 * {@code @Entity @Table} 决定实际表名，再通过各日志注解的
 * {@code entity = SomeLog.class} 参数告知切面使用哪个实体。
 * </p>
 *
 * <h3>继承体系</h3>
 * <pre>
 * BaseEntity          (id / JPA审计 / 软删除 / version)
 *   └── TenantEntity  (+ tenantId)
 *         └── DataTenantEntity (+ remark / enabled / status)
 *               └── BaseLog  [公共日志字段：traceId / username / clientIp / status …]
 *                     └── SysXxxLog  (业务项目自定义，按需扩展专属字段)
 * </pre>
 *
 * <h3>业务项目使用示例</h3>
 * <pre>{@code
 * @Entity
 * @Table(name = "sys_login_log")
 * public class SysLoginLog extends BaseLog {
 *     private String action;    // 登录 / 登出
 *     private String userAgent;
 *     private String browser;
 *     private String os;
 * }
 * }</pre>
 *
 * <h3>status 约定</h3>
 * <p>
 * 存储状态字符串，典型值：{@code SUCCESS / FAIL / RUNNING / WARN / SKIP}。
 * 业务项目可通过 jpa-plus {@code @DictLabel} 配置字典翻译展示。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Getter
@Setter
@jakarta.persistence.MappedSuperclass
public abstract class BaseLog extends DataTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    // ─────────────────────── 链路追踪 ───────────────────────

    /**
     * 链路追踪 ID（来自请求头 {@code X-Trace-Id} 或框架自动生成）
     */
    @Column(name = "trace_id", length = 64, comment = "链路追踪ID")
    private String traceId;

    // ─────────────────────── 操作人信息 ─────────────────────

    /**
     * 操作用户 ID
     */
    @Column(name = "user_id", length = 64, comment = "操作用户ID")
    private String userId;

    /**
     * 操作人/登录用户名
     */
    @Column(name = "username", length = 64, comment = "操作用户名")
    private String username;

    /**
     * 客户端 IP
     */
    @Column(name = "client_ip", length = 50, comment = "客户端IP")
    private String clientIp;

    /**
     * 地理位置（由 IP 解析，如 "中国 广东 深圳"）
     */
    @Column(name = "location", length = 100, comment = "地理位置")
    private String location;

    /**
     * 消息/错误提示（成功时为操作摘要，失败时为错误原因）
     */
    @Column(name = "message", length = 500, comment = "日志消息")
    private String message;

    // ─────────────────────── 时间 ───────────────────────────

    /**
     * 日志发生时间（具体子类可进一步细化为 loginTime / operateTime / startTime 等）
     */
    @Column(name = "log_time", comment = "日志发生时间")
    private LocalDateTime logTime;

}
