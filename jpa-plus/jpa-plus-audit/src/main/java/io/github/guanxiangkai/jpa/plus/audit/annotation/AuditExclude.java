package io.github.guanxiangkai.jpa.plus.audit.annotation;

import java.lang.annotation.*;

/**
 * 审计字段排除注解
 *
 * <p>标注在实体字段上，声明该字段不参与 {@link io.github.guanxiangkai.jpa.plus.audit.interceptor.SnapshotAuditInterceptor}
 * 的快照采集与差异对比。</p>
 *
 * <h3>典型使用场景</h3>
 * <ul>
 *   <li>标注了 {@code @Encrypt} 的加密字段（审计日志中不应出现密文）</li>
 *   <li>标注了 {@code @Desensitize} 的脱敏字段</li>
 *   <li>体积较大、对审计无意义的 BLOB / CLOB 字段</li>
 *   <li>框架内部维护的乐观锁版本号等技术字段</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Entity
 * public class User {
 *
 *     @Encrypt
 *     @AuditExclude // 加密字段不参与审计快照
 *     private String phone;
 *
 *     @AuditExclude // 大字段不参与审计快照
 *     @Column(columnDefinition = "TEXT")
 *     private String remark;
 *
 *     private String name;  // 普通字段照常参与审计快照
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.jpa.plus.audit.interceptor.SnapshotAuditInterceptor
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditExclude {
}

