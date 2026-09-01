package io.github.guanxiangkai.jpa.plus.starter.tenant;

import io.github.guanxiangkai.jpa.plus.interceptor.tenant.spi.TenantIdProvider;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JpaPlusTenantSaveEventListenerTest {

    @Test
    void preInsertShouldPreserveExplicitTenantWithoutResolvingCurrentContext() {
        TenantIdProvider tenantIdProvider = mock(TenantIdProvider.class);
        HibernateTenantContext tenantContext = new HibernateTenantContext(
                tenantIdProvider, "tenantId", "tenant_id", java.util.Set.of());
        JpaPlusTenantSaveEventListener listener = new JpaPlusTenantSaveEventListener(tenantContext);
        EntityPersister persister = mock(EntityPersister.class);
        when(persister.getPropertyNames()).thenReturn(new String[]{"tenantId"});
        Object[] state = {"tenant-explicit"};
        PreInsertEvent event = mock(PreInsertEvent.class);
        when(event.getEntity()).thenReturn(new Object());
        when(event.getPersister()).thenReturn(persister);
        when(event.getState()).thenReturn(state);

        listener.onPreInsert(event);

        assertThat(state).containsExactly("tenant-explicit");
        verifyNoInteractions(tenantIdProvider);
    }
}
