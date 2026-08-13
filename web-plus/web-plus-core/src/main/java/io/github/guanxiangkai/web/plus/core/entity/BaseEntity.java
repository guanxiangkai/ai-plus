package io.github.guanxiangkai.web.plus.core.entity;

import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.CreateBy;
import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.CreateTime;
import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.UpdateBy;
import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.UpdateTime;
import io.github.guanxiangkai.jpa.plus.field.id.annotation.AutoId;
import io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.annotation.LogicDelete;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体类
 * <p>
 * 提供所有实体的通用字段：Snowflake/UUID 主键（jpa-plus {@code @AutoId}）、
 * 自动审计（创建/更新时间 + 操作人，jpa-plus 自动填充引擎）、
 * 软删除（{@code @LogicDelete}，jpa-plus 拦截器自动注入 {@code deleted=false} 条件及改写 DELETE → UPDATE）
 * 以及乐观锁版本号。
 * </p>
 *
 * <h3>实体继承体系</h3>
 * <pre>
 * BaseEntity   （id / 审计 / 软删除 / version）
 *   └── DataEntity  （+ remark / enabled / status）
 *   └── TenantEntity （+ tenantId）
 *         ├── DeptEntity （+ deptId / deptName）
 *         │     └── DataDeptEntity / SortableDeptEntity
 *         └── DataTenantEntity / SortableTenantEntity
 * </pre>
 *
 * <h3>软删除规则</h3>
 * <ul>
 *   <li>直接调用 {@code repository.delete(entity)} 或 {@code repository.deleteById(id)} 即可触发软删除，
 *       jpa-plus 拦截器将 DELETE SQL 改写为 {@code UPDATE ... SET deleted = true}</li>
 *   <li>所有查询（包括方法名查询、Specification 查询、JPQL）均自动追加 {@code deleted = false} 过滤</li>
 * </ul>
 *
 * <h3>审计字段说明</h3>
 * <p>
 * {@code @CreateBy} / {@code @UpdateBy} 由 jpa-plus 通过 {@code CurrentUserProvider} SPI 自动填充；
 * web-plus-web 的 {@code WebPlusCoreAutoConfiguration} 自动注册委托给 web-plus 当前用户 SPI 的桥接实现。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Getter
@Setter
@jakarta.persistence.MappedSuperclass
@SQLRestriction("deleted = false")
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（jpa-plus @AutoId：默认 UUID，可通过 jpa-plus.id.strategy 切换为 Snowflake）
     */
    @Id
    @AutoId
    @Column(name = "id", length = 64, updatable = false, comment = "主键")
    private String id;

    /**
     * 创建时间（jpa-plus 自动填充）
     */
    @CreateTime
    @Column(name = "create_time", updatable = false, comment = "创建时间")
    private LocalDateTime createTime;

    /**
     * 创建人 ID（jpa-plus 通过 {@code CurrentUserProvider} SPI 自动填充）
     */
    @CreateBy
    @Column(name = "create_by", length = 64, updatable = false, comment = "创建人ID")
    private String createBy;

    /**
     * 最后更新时间（jpa-plus 自动填充）
     */
    @UpdateTime
    @Column(name = "update_time", comment = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 最后更新人 ID（jpa-plus 通过 {@code CurrentUserProvider} SPI 自动填充）
     */
    @UpdateBy
    @Column(name = "update_by", length = 64, comment = "更新人ID")
    private String updateBy;

    /**
     * 软删除标志（{@code false} = 正常，{@code true} = 已删除）
     * <p>
     * 由 jpa-plus {@code @LogicDelete} 拦截器自动管理：
     * 查询自动追加 {@code WHERE deleted = false}，DELETE 自动改写为 {@code UPDATE ... SET deleted = true}。
     * </p>
     */
    @LogicDelete
    @Column(name = "deleted", nullable = false, comment = "软删除标志")
    private Boolean deleted = false;

    /**
     * 乐观锁版本号（jpa-plus 逻辑删除时自动递增，与 {@code @LogicDelete} 协同保证并发安全）
     */
    @Version
    @Column(name = "version", comment = "乐观锁版本号")
    private Integer version = 0;

}
