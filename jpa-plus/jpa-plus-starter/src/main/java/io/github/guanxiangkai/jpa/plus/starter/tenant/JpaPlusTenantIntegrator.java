package io.github.guanxiangkai.jpa.plus.starter.tenant;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

/**
 * 注册租户字段保存事件。
 */
public final class JpaPlusTenantIntegrator implements Integrator {

    private final JpaPlusTenantSaveEventListener saveEventListener;

    public JpaPlusTenantIntegrator(HibernateTenantContext tenantContext) {
        this.saveEventListener = new JpaPlusTenantSaveEventListener(tenantContext);
    }

    @Override
    public void integrate(Metadata metadata, BootstrapContext bootstrapContext,
                          SessionFactoryImplementor sessionFactory) {
        EventListenerRegistry registry = sessionFactory.getServiceRegistry()
                .requireService(EventListenerRegistry.class);
        registry.appendListeners(EventType.PRE_INSERT, saveEventListener);
        registry.appendListeners(EventType.PRE_UPDATE, saveEventListener);
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory,
                             SessionFactoryServiceRegistry serviceRegistry) {
        // 无需释放资源。
    }
}
