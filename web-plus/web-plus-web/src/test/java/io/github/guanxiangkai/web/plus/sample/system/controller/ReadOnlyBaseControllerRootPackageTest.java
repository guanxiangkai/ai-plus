package io.github.guanxiangkai.web.plus.sample.system.controller;

import io.github.guanxiangkai.web.plus.core.model.PageQuery;
import io.github.guanxiangkai.web.plus.web.controller.ReadOnlyBaseController;
import io.github.guanxiangkai.web.plus.web.service.IReadOnlyService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOnlyBaseControllerRootPackageTest {

    @Test
    void shouldExposePublicSpelMetadata() {
        RoleReadOnlyController controller = new RoleReadOnlyController();

        assertThat(controller.getPermissionPrefix()).isEqualTo("system:roleReadOnly");
        assertThat(controller.getModuleName()).isEqualTo("System");
    }

    private static final class RoleReadOnlyController
            extends ReadOnlyBaseController<PageQuery, Object, Object> {

        @Override
        protected IReadOnlyService<PageQuery, Object, Object> getService() {
            return null;
        }
    }
}
