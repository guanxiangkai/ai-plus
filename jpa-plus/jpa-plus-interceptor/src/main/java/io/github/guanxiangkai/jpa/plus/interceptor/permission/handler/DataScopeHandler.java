package io.github.guanxiangkai.jpa.plus.interceptor.permission.handler;

import io.github.guanxiangkai.jpa.plus.interceptor.permission.enums.DataScopeType;
import io.github.guanxiangkai.jpa.plus.query.ast.Condition;

import java.util.Collection;

/**
 * 数据权限处理器接口（SPI）
 *
 * <p>用户实现此接口，提供当前登录用户的部门 ID、用户 ID 以及可访问的部门列表。
 * 框架通过此接口获取上下文信息，结合 {@link DataScopeType} 构建权限过滤条件。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Component
 * public class MyDataScopeHandler implements DataScopeHandler {
 *
 *     @Override
 *     public Object getCurrentUserId() {
 *         return SecurityContextHolder.getContext().getAuthentication().getName();
 *     }
 *
 *     @Override
 *     public Object getCurrentDeptId() {
 *         return SecurityUtils.getLoginUser().getDeptId();
 *     }
 *
 *     @Override
 *     public Collection<?> getDeptAndChildIds() {
 *         return deptService.selectDeptAndChildIds(getCurrentDeptId());
 *     }
 * }
 * }</pre>
 *
 * <p><b>设计模式：</b>策略接口模式（Strategy） —— 解耦权限数据获取与条件构建</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface DataScopeHandler {

    /**
     * 获取当前登录用户 ID
     *
     * <p>用于 {@link DataScopeType#SELF} 模式：{@code create_by = :userId}</p>
     *
     * @return 用户 ID，{@code null} 表示未登录（不追加权限条件）
     */
    Object getCurrentUserId();

    /**
     * 获取当前用户所属部门 ID
     *
     * <p>用于 {@link DataScopeType#DEPT} 模式：{@code dept_id = :deptId}</p>
     *
     * @return 部门 ID，{@code null} 表示无部门信息
     */
    Object getCurrentDeptId();

    /**
     * 获取当前用户可访问的部门 ID 集合（含本部门及所有子部门）
     *
     * <p>用于 {@link DataScopeType#DEPT_AND_CHILD} 模式：{@code dept_id IN (:deptIds)}</p>
     *
     * @return 部门 ID 集合，为空时等同于无权访问任何数据
     */
    Collection<?> getDeptAndChildIds();

    /**
     * 构建自定义数据权限条件
     *
     * <p>仅在 {@link DataScopeType#CUSTOM} 模式下被调用，
     * 允许用户完全自定义 SQL 条件（如多角色联合权限、跨组织访问等）。</p>
     *
     * @param entityClass 当前查询的实体类
     * @return 自定义权限条件 AST 节点，{@code null} 表示无限制
     */
    default Condition customCondition(Class<?> entityClass) {
        return null;
    }
}

