package io.github.guanxiangkai.jpa.plus.audit.interceptor;

import io.github.guanxiangkai.jpa.plus.audit.event.AuditEventPublisher;
import io.github.guanxiangkai.jpa.plus.audit.event.DataAuditEvent;
import io.github.guanxiangkai.jpa.plus.audit.snapshot.AuditSnapshot;
import io.github.guanxiangkai.jpa.plus.core.interceptor.InterceptorChain;
import io.github.guanxiangkai.jpa.plus.core.interceptor.Phase;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;
import io.github.guanxiangkai.jpa.plus.core.model.SaveInvocation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SnapshotAuditInterceptor 回归测试
 * <p>
 * 验证：快照 diff 在 SAVE 操作中必须是非空的（before 和 after 状态不同），
 * 且拦截器 phase 为 BEFORE（以便能在核心执行前后各采集一次快照）。
 */
class SnapshotAuditInterceptorTest {

    private static AuditEventPublisher capture(List<DataAuditEvent> published) {
        return event -> {
            if (event instanceof DataAuditEvent dae) published.add(dae);
        };
    }

    @Test
    void phase_isBefore() {
        SnapshotAuditInterceptor interceptor = new SnapshotAuditInterceptor(
                event -> {
                }, mock(EntityManager.class));
        assertThat(interceptor.phase()).isEqualTo(Phase.BEFORE);
    }

    @Test
    void saveOperation_producesNonEmptyDiff_whenCoreExecutionChangesEntity() throws Throwable {
        List<DataAuditEvent> published = new ArrayList<>();
        AuditEventPublisher publisher = event -> {
            if (event instanceof DataAuditEvent dae) published.add(dae);
        };

        SnapshotAuditInterceptor interceptor = new SnapshotAuditInterceptor(
                publisher, mock(EntityManager.class));

        SampleEntity entity = new SampleEntity("Alice");
        SaveInvocation invocation = new SaveInvocation(SampleEntity.class, entity);

        // Build an InterceptorChain with just this interceptor.
        // The CoreExecution simulates JPA save: assigns an ID (typical JPA persist side-effect).
        InterceptorChain chain = new InterceptorChain(List.of(interceptor));
        chain.proceed(invocation, inv -> {
            // Simulate JPA persist populating the generated ID
            ((SaveInvocation) inv).entity();  // access entity
            entity.id = 42L;
            return entity;
        });

        assertThat(published).hasSize(1);
        DataAuditEvent event = published.getFirst();
        assertThat(event.operation()).isEqualTo(OperationType.SAVE);

        AuditSnapshot snapshot = event.snapshot();
        assertThat(snapshot).isNotNull();
        // The ID field changed from null → 42 during the simulated JPA save
        assertThat(snapshot.hasChanges()).isTrue();
        assertThat(snapshot.diffs()).containsKey("id");
        assertThat(snapshot.diffs().get("id").before()).isNull();
        assertThat(snapshot.diffs().get("id").after()).isEqualTo(42L);
    }

    @Test
    void saveOperation_emptyDiff_whenPersistedStateMatchesManagedEntity() throws Throwable {
        List<DataAuditEvent> published = new ArrayList<>();
        EntityManager entityManager = mock(EntityManager.class);
        EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);
        PersistenceUnitUtil persistenceUnitUtil = mock(PersistenceUnitUtil.class);
        EntityManager snapshotEntityManager = mock(EntityManager.class);
        SnapshotAuditInterceptor interceptor = new SnapshotAuditInterceptor(capture(published), entityManager);

        SampleEntity entity = new SampleEntity("Bob");
        entity.id = 1L;
        entity.version = 3L;
        SampleEntity persisted = new SampleEntity("Bob");
        persisted.id = 1L;
        persisted.version = 3L;
        SaveInvocation invocation = new SaveInvocation(SampleEntity.class, entity);

        when(entityManager.getEntityManagerFactory()).thenReturn(entityManagerFactory);
        when(entityManagerFactory.getPersistenceUnitUtil()).thenReturn(persistenceUnitUtil);
        when(entityManagerFactory.createEntityManager()).thenReturn(snapshotEntityManager);
        when(persistenceUnitUtil.getIdentifier(entity)).thenReturn(1L);
        when(snapshotEntityManager.find(SampleEntity.class, 1L)).thenReturn(persisted);

        InterceptorChain chain = new InterceptorChain(List.of(interceptor));
        chain.proceed(invocation, inv -> entity);

        assertThat(published).hasSize(1);
        assertThat(published.getFirst().snapshot().hasChanges()).isFalse();
        verify(snapshotEntityManager).find(SampleEntity.class, 1L);
        verify(entityManager, never()).find(any(), any());
    }

    @Test
    void saveOperation_usesIsolatedBeforeStateAndMergeResultAsAfterState() throws Throwable {
        List<DataAuditEvent> published = new ArrayList<>();
        EntityManager entityManager = mock(EntityManager.class);
        EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);
        PersistenceUnitUtil persistenceUnitUtil = mock(PersistenceUnitUtil.class);
        EntityManager snapshotEntityManager = mock(EntityManager.class);
        SnapshotAuditInterceptor interceptor = new SnapshotAuditInterceptor(capture(published), entityManager);

        SampleEntity detached = new SampleEntity("Alice-v2");
        detached.id = 7L;
        detached.version = null;

        SampleEntity persisted = new SampleEntity("Alice");
        persisted.id = 7L;
        persisted.version = 1L;

        SampleEntity merged = new SampleEntity("Alice-v2");
        merged.id = 7L;
        merged.version = 2L;

        when(entityManager.getEntityManagerFactory()).thenReturn(entityManagerFactory);
        when(entityManagerFactory.getPersistenceUnitUtil()).thenReturn(persistenceUnitUtil);
        when(entityManagerFactory.createEntityManager()).thenReturn(snapshotEntityManager);
        when(persistenceUnitUtil.getIdentifier(detached)).thenReturn(7L);
        when(snapshotEntityManager.find(SampleEntity.class, 7L)).thenReturn(persisted);

        InterceptorChain chain = new InterceptorChain(List.of(interceptor));
        chain.proceed(new SaveInvocation(SampleEntity.class, detached), inv -> merged);

        assertThat(published).hasSize(1);
        DataAuditEvent event = published.getFirst();
        assertThat(event.entity()).isSameAs(merged);
        assertThat(event.snapshot().diffs()).containsKeys("name", "version");
        assertThat(event.snapshot().diffs().get("name").before()).isEqualTo("Alice");
        assertThat(event.snapshot().diffs().get("name").after()).isEqualTo("Alice-v2");
        assertThat(event.snapshot().diffs().get("version").before()).isEqualTo(1L);
        assertThat(event.snapshot().diffs().get("version").after()).isEqualTo(2L);
    }

    static class SampleEntity {
        String name;
        @Id
        Long id;
        Long version;

        SampleEntity(String name) {
            this.name = name;
        }
    }
}
