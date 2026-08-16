package io.github.guanxiangkai.web.plus.web.interceptor;

import io.github.guanxiangkai.jpa.plus.interceptor.permission.handler.DataScopeHandler;
import io.github.guanxiangkai.jpa.plus.query.ast.Condition;
import io.github.guanxiangkai.web.plus.security.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Set;

/**
 * JPA Plus 数据权限处理器（{@link DataScopeHandler} SPI 实现）
 * <p>
 * 超级管理员所有方法返回 {@code null}，跳过权限过滤。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class WebPlusDataScopeHandler implements DataScopeHandler {

    @Override
    public Object getCurrentUserId() {
        try {
            if (SecurityUtils.isSuperAdmin()) return null;
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            log.debug("获取用户 ID 失败: exception={}", e.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public Object getCurrentDeptId() {
        try {
            if (SecurityUtils.isSuperAdmin()) return null;
            return SecurityUtils.getDeptId();
        } catch (Exception e) {
            log.debug("获取部门 ID 失败: exception={}", e.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public Collection<?> getDeptAndChildIds() {
        try {
            if (SecurityUtils.isSuperAdmin()) return null;
            Set<String> deptIds = SecurityUtils.getDeptIds();
            return deptIds.isEmpty() ? null : deptIds;
        } catch (Exception e) {
            log.debug("获取部门 ID 集合失败: exception={}", e.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public Condition customCondition(Class<?> entityClass) {
        return null;
    }
}
