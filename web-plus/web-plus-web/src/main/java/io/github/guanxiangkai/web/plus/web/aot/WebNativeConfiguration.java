package io.github.guanxiangkai.web.plus.web.aot;

import io.github.guanxiangkai.web.plus.core.converter.EntityConverter;
import io.github.guanxiangkai.web.plus.core.entity.BaseEntity;
import io.github.guanxiangkai.web.plus.core.entity.DataDeptEntity;
import io.github.guanxiangkai.web.plus.core.entity.DataTenantEntity;
import io.github.guanxiangkai.web.plus.core.entity.DeptEntity;
import io.github.guanxiangkai.web.plus.core.entity.SortableDeptEntity;
import io.github.guanxiangkai.web.plus.core.entity.SortableTenantEntity;
import io.github.guanxiangkai.web.plus.core.entity.TenantEntity;
import io.github.guanxiangkai.web.plus.core.enums.HttpMethod;
import io.github.guanxiangkai.web.plus.core.enums.HttpStatus;
import io.github.guanxiangkai.web.plus.core.model.PageRequest;
import io.github.guanxiangkai.web.plus.core.model.PageResponse;
import io.github.guanxiangkai.web.plus.web.controller.BaseController;
import io.github.guanxiangkai.web.plus.web.controller.ReadOnlyBaseController;
import io.github.guanxiangkai.web.plus.web.properties.CorsProperties;
import io.github.guanxiangkai.web.plus.web.repository.BaseRepository;
import io.github.guanxiangkai.web.plus.web.service.IBaseService;
import io.github.guanxiangkai.web.plus.web.service.IReadOnlyService;
import io.github.guanxiangkai.web.plus.web.service.impl.BaseServiceImpl;
import io.github.guanxiangkai.web.plus.web.service.impl.ReadOnlyBaseServiceImpl;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.Set;

/**
 * Web Plus Web 模块的 GraalVM Native Image 运行时提示。
 *
 * @author guanxiangkai
 * @since 4.0.0
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(WebNativeConfiguration.Registrar.class)
public class WebNativeConfiguration {

    static final class Registrar implements RuntimeHintsRegistrar {

        private static final Set<Class<?>> REFLECTION_TYPES = Set.of(
                HttpStatus.class, HttpStatus.StatusCategory.class, HttpMethod.class,
                CorsProperties.class, PageRequest.class, PageResponse.class,
                BaseEntity.class, TenantEntity.class, DeptEntity.class,
                DataTenantEntity.class, DataDeptEntity.class,
                SortableTenantEntity.class, SortableDeptEntity.class,
                EntityConverter.class, BaseRepository.class,
                IReadOnlyService.class, IBaseService.class,
                ReadOnlyBaseServiceImpl.class, BaseServiceImpl.class,
                ReadOnlyBaseController.class, BaseController.class
        );

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            REFLECTION_TYPES.forEach(type -> hints.reflection().registerType(type,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS));
            hints.resources().registerPattern("META-INF/spring/*");
        }
    }
}
