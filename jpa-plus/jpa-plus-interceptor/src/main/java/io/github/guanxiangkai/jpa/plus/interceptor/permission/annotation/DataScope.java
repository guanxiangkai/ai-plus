package io.github.guanxiangkai.jpa.plus.interceptor.permission.annotation;

import io.github.guanxiangkai.jpa.plus.interceptor.permission.enums.DataScopeEnum;
import io.github.guanxiangkai.jpa.plus.interceptor.permission.enums.DataScopeType;

import java.lang.annotation.*;

/**
 * 数据权限注解
 *
 * <p>标注在实体类或 Repository 方法上，声明该实体/查询的数据权限范围。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 内置类型
 * @DataScope(type = DataScopeType.DEPT_AND_CHILD)
 * public class Order { ... }
 *
 * // 自定义类型
 * @DataScope(customType = MyDataScope.REGION.class)
 * public class RegionOrder { ... }
 * }</pre>
 *
 * @author guanxiangkai
 * @see DataScopeType
 * @see DataScopeEnum
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 内置数据权限范围类型（默认本部门）
     */
    DataScopeType type() default DataScopeType.DEPT;

    /**
     * 自定义数据权限范围类型（优先于 {@link #type()}）
     *
     * <p>指定一个实现了 {@link DataScopeEnum} 的枚举或类。
     * 设为默认值 {@code DataScopeEnum.class} 表示不使用自定义类型。</p>
     */
    Class<? extends DataScopeEnum> customType() default DataScopeEnum.class;

    /**
     * 部门字段列名（默认 "dept_id"）
     */
    String deptColumn() default "dept_id";

    /**
     * 创建人字段列名（默认 "create_by"）
     */
    String userColumn() default "create_by";
}
