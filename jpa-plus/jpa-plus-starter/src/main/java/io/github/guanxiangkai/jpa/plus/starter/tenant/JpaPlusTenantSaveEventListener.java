package io.github.guanxiangkai.jpa.plus.starter.tenant;

import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;

/**
 * 保存租户实体前自动补齐当前租户 ID。
 */
final class JpaPlusTenantSaveEventListener implements PreInsertEventListener, PreUpdateEventListener {

    private final HibernateTenantContext tenantContext;

    JpaPlusTenantSaveEventListener(HibernateTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    public boolean onPreInsert(PreInsertEvent event) {
        fillTenantId(event.getEntity(), event.getPersister(), event.getState());
        return false;
    }

    @Override
    public boolean onPreUpdate(PreUpdateEvent event) {
        fillTenantId(event.getEntity(), event.getPersister(), event.getState());
        return false;
    }

    private void fillTenantId(Object entity, EntityPersister persister, Object[] state) {
        if (entity == null || persister == null || state == null) {
            return;
        }
        int propertyIndex = tenantPropertyIndex(persister.getPropertyNames());
        if (propertyIndex < 0 || propertyIndex >= state.length || !tenantContext.shouldFill(state[propertyIndex])) {
            return;
        }

        String tenantId = tenantContext.requireTenantId();
        state[propertyIndex] = tenantId;
        persister.setValues(entity, state);
    }

    private int tenantPropertyIndex(String[] propertyNames) {
        if (propertyNames == null) {
            return -1;
        }
        String tenantProperty = tenantContext.tenantProperty();
        for (int i = 0; i < propertyNames.length; i++) {
            if (tenantProperty.equals(propertyNames[i])) {
                return i;
            }
        }
        return -1;
    }
}
