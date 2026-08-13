package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.interceptor.tenant.spi.TenantIdProvider;
import io.github.guanxiangkai.jpa.plus.starter.tenant.HibernateTenantContext;
import io.github.guanxiangkai.jpa.plus.starter.tenant.JpaPlusTenantIntegrator;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.jpa.boot.spi.JpaSettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JpaPlusHibernateTenantAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JpaPlusHibernateTenantAutoConfiguration.class))
            .withBean(TenantIdProvider.class, () -> () -> "tenant-1");

    @Test
    void autoConfiguration_registersHibernateCustomizerWhenTenantProviderExists() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(HibernatePropertiesCustomizer.class));
    }

    @Test
    void autoConfiguration_backsOffWhenHibernateTenantDisabled() {
        contextRunner
                .withPropertyValues("jpa-plus.tenant.hibernate.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(HibernatePropertiesCustomizer.class));
    }

    @Test
    void customizer_preservesExistingIntegratorProviderAndRegistersTenantContext() {
        JpaPlusHibernateTenantAutoConfiguration configuration = new JpaPlusHibernateTenantAutoConfiguration();
        HibernatePropertiesCustomizer customizer = configuration.jpaPlusHibernateTenantCustomizer(
                () -> "tenant-1",
                "tenantId",
                "tenant_id",
                "0, -1"
        );
        Integrator existingIntegrator = mock(Integrator.class);
        Map<String, Object> hibernateProperties = new HashMap<>();
        hibernateProperties.put(JpaSettings.INTEGRATOR_PROVIDER,
                (IntegratorProvider) () -> List.of(existingIntegrator));

        customizer.customize(hibernateProperties);

        assertThat(hibernateProperties.get(HibernateTenantContext.SETTING_KEY))
                .isInstanceOf(HibernateTenantContext.class);
        IntegratorProvider provider = (IntegratorProvider) hibernateProperties.get(JpaSettings.INTEGRATOR_PROVIDER);
        assertThat(provider.getIntegrators())
                .contains(existingIntegrator)
                .anySatisfy(integrator -> assertThat(integrator).isInstanceOf(JpaPlusTenantIntegrator.class));
    }

    @Test
    void customizer_doesNotDuplicateTenantIntegratorWhenProviderAlreadyContainsOne() {
        JpaPlusHibernateTenantAutoConfiguration configuration = new JpaPlusHibernateTenantAutoConfiguration();
        HibernatePropertiesCustomizer customizer = configuration.jpaPlusHibernateTenantCustomizer(
                () -> "tenant-1",
                "tenantId",
                "tenant_id",
                ""
        );
        HibernateTenantContext context = new HibernateTenantContext(
                () -> "tenant-1",
                "tenantId",
                "tenant_id",
                Set.of()
        );
        Integrator existingTenantIntegrator = new JpaPlusTenantIntegrator(context);
        Map<String, Object> hibernateProperties = new HashMap<>();
        hibernateProperties.put(JpaSettings.INTEGRATOR_PROVIDER,
                (IntegratorProvider) () -> List.of(existingTenantIntegrator));

        customizer.customize(hibernateProperties);

        IntegratorProvider provider = (IntegratorProvider) hibernateProperties.get(JpaSettings.INTEGRATOR_PROVIDER);
        assertThat(provider.getIntegrators())
                .filteredOn(integrator -> integrator instanceof JpaPlusTenantIntegrator)
                .hasSize(1);
    }
}
