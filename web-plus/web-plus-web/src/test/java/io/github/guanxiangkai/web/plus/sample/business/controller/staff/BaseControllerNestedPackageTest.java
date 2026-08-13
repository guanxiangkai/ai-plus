package io.github.guanxiangkai.web.plus.sample.business.controller.staff;

import io.github.guanxiangkai.web.plus.core.entity.BaseEntity;
import io.github.guanxiangkai.web.plus.core.model.PageQuery;
import io.github.guanxiangkai.web.plus.web.controller.BaseController;
import io.github.guanxiangkai.web.plus.web.service.IBaseService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseControllerNestedPackageTest {

    @Test
    void shouldInferModuleFromTheSegmentAfterController() {
        PersonnelController controller = new PersonnelController();

        assertThat(controller.getPermissionPrefix()).isEqualTo("staff:personnel");
        assertThat(controller.getModuleName()).isEqualTo("Staff");
    }

    private static final class PersonnelController
            extends BaseController<PageQuery, Object, Object, Object, Object, BaseEntity> {

        @Override
        protected IBaseService<PageQuery, Object, Object, Object, Object, BaseEntity> getService() {
            return null;
        }
    }
}
