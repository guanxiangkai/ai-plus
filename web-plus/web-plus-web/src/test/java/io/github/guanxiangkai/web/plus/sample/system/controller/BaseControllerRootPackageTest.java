package io.github.guanxiangkai.web.plus.sample.system.controller;

import io.github.guanxiangkai.web.plus.core.entity.BaseEntity;
import io.github.guanxiangkai.web.plus.core.model.PageQuery;
import io.github.guanxiangkai.web.plus.web.controller.BaseController;
import io.github.guanxiangkai.web.plus.web.service.IBaseService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseControllerRootPackageTest {

    @Test
    void shouldInferModuleWhenControllerIsTheLastPackageSegment() {
        RoleController controller = new RoleController();

        assertThat(controller.getPermissionPrefix()).isEqualTo("system:role");
        assertThat(controller.getModuleName()).isEqualTo("System");
    }

    private static final class RoleController
            extends BaseController<PageQuery, Object, Object, Object, Object, BaseEntity> {

        @Override
        protected IBaseService<PageQuery, Object, Object, Object, Object, BaseEntity> getService() {
            return null;
        }
    }
}
