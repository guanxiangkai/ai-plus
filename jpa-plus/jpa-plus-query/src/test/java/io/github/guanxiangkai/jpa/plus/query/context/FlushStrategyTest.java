package io.github.guanxiangkai.jpa.plus.query.context;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlushStrategyTest {

    @Test
    void always_flushesBeforeQuery() {
        EntityManager entityManager = mock(EntityManager.class);

        new FlushStrategy(FlushMode.ALWAYS).flushIfNeeded(entityManager);

        verify(entityManager).flush();
    }

    @Test
    void never_skipsFlush() {
        EntityManager entityManager = mock(EntityManager.class);

        new FlushStrategy(FlushMode.NEVER).flushIfNeeded(entityManager);

        verify(entityManager, never()).flush();
    }

    @Test
    void auto_flushesWhenHibernateSessionIsDirty() {
        EntityManager entityManager = mock(EntityManager.class);
        Session session = mock(Session.class);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.isDirty()).thenReturn(true);

        new FlushStrategy(FlushMode.AUTO).flushIfNeeded(entityManager);

        verify(entityManager).flush();
    }

    @Test
    void auto_skipsFlushWhenHibernateSessionIsClean() {
        EntityManager entityManager = mock(EntityManager.class);
        Session session = mock(Session.class);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.isDirty()).thenReturn(false);

        new FlushStrategy(FlushMode.AUTO).flushIfNeeded(entityManager);

        verify(entityManager, never()).flush();
    }

    @Test
    void auto_conservativelyFlushesWhenHibernateStateCannotBeChecked() {
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.unwrap(Session.class)).thenThrow(new PersistenceException("unwrap failed"));

        new FlushStrategy(FlushMode.AUTO).flushIfNeeded(entityManager);

        verify(entityManager).flush();
    }
}
