package io.github.guanxiangkai.jpa.plus.starter.tenant;

import org.hibernate.MappingException;
import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Selectable;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.resource.beans.spi.ManagedBean;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 在 Hibernate 元数据构建阶段为租户实体挂载自动过滤器。
 */
public final class JpaPlusTenantMappingContributor implements AdditionalMappingContributor {

    @Override
    public String getContributorName() {
        return "jpa-plus-tenant";
    }

    @Override
    public void contribute(AdditionalMappingContributions contributions,
                           InFlightMetadataCollector metadataCollector,
                           ResourceStreamLocator resourceStreamLocator,
                           MetadataBuildingContext buildingContext) {
        HibernateTenantContext tenantContext = tenantContext(buildingContext);
        if (tenantContext == null) {
            return;
        }

        registerFilterDefinition(metadataCollector, tenantContext);
        metadataCollector.getEntityBindingMap().values()
                .forEach(persistentClass -> applyTenantFilter(persistentClass, tenantContext));
    }

    private HibernateTenantContext tenantContext(MetadataBuildingContext buildingContext) {
        Object value = buildingContext.getBootstrapContext()
                .getServiceRegistry()
                .requireService(ConfigurationService.class)
                .getSettings()
                .get(HibernateTenantContext.SETTING_KEY);
        return value instanceof HibernateTenantContext context ? context : null;
    }

    private void registerFilterDefinition(InFlightMetadataCollector metadataCollector,
                                          HibernateTenantContext tenantContext) {
        if (metadataCollector.getFilterDefinition(HibernateTenantContext.FILTER_NAME) != null) {
            return;
        }
        JdbcMapping tenantIdType = metadataCollector.getTypeConfiguration()
                .getBasicTypeForJavaType(String.class);
        JpaPlusTenantParameterResolver resolver = new JpaPlusTenantParameterResolver(tenantContext);
        ManagedBean<? extends Supplier<?>> resolverBean = new JpaPlusManagedBean<>(
                JpaPlusTenantParameterResolver.class,
                resolver
        );
        metadataCollector.addFilterDefinition(new FilterDefinition(
                HibernateTenantContext.FILTER_NAME,
                "",
                true,
                true,
                Map.of(HibernateTenantContext.PARAMETER_NAME, tenantIdType),
                Map.of(HibernateTenantContext.PARAMETER_NAME, resolverBean)
        ));
    }

    private void applyTenantFilter(PersistentClass persistentClass, HibernateTenantContext tenantContext) {
        if (hasTenantFilter(persistentClass)) {
            return;
        }
        Property property = tenantProperty(persistentClass, tenantContext.tenantProperty());
        if (property == null) {
            return;
        }
        assertStringTenantProperty(persistentClass, property);
        String selectable = tenantSelectable(property, tenantContext.tenantColumn());
        persistentClass.addFilter(
                HibernateTenantContext.FILTER_NAME,
                selectable + " = :" + HibernateTenantContext.PARAMETER_NAME,
                true,
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }

    private boolean hasTenantFilter(PersistentClass persistentClass) {
        return persistentClass.getFilters().stream()
                .anyMatch(filter -> HibernateTenantContext.FILTER_NAME.equals(filter.getName()));
    }

    private Property tenantProperty(PersistentClass persistentClass, String tenantProperty) {
        try {
            return persistentClass.getProperty(tenantProperty);
        } catch (MappingException ignored) {
            return null;
        }
    }

    private void assertStringTenantProperty(PersistentClass persistentClass, Property property) {
        String returnedClassName = property.getReturnedClassName();
        if (returnedClassName == null || String.class.getName().equals(returnedClassName)) {
            return;
        }
        throw new MappingException("jpa-plus tenant property must be java.lang.String: "
                + persistentClass.getEntityName() + "." + property.getName());
    }

    private String tenantSelectable(Property property, String configuredColumn) {
        if (property.getColumnSpan() != 1) {
            throw new MappingException("jpa-plus tenant property must map to a single column or formula: "
                    + property.getPersistentClass().getEntityName() + "." + property.getName());
        }
        Selectable selectable = property.getSelectables().getFirst();
        if (selectable instanceof Formula formula) {
            return formula.getFormula();
        }
        if (selectable instanceof Column column) {
            return column.getName();
        }
        return configuredColumn;
    }
}
