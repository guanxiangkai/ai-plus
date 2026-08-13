package io.github.guanxiangkai.jpa.plus.datasource.tx;

import io.github.guanxiangkai.jpa.plus.datasource.context.JpaPlusContext;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.Callable;

import static org.mockito.Mockito.*;

class DynamicTransactionManagerTest {

    @Test
    void commit_usesTransactionManagerBoundAtTransactionStart_evenWhenDatasourceContextChanges() throws Exception {
        DynamicTransactionManager manager = new DynamicTransactionManager("master");
        DataSource master = mockDataSource("master");
        DataSource slave = mockDataSource("slave");
        manager.registerDataSource("master", master);
        manager.registerDataSource("slave", slave);

        TransactionStatus status = manager.getTransaction(new DefaultTransactionDefinition());

        JpaPlusContext.runWithDS("slave", () -> manager.commit(status));

        verify(connection(master)).commit();
        verify(connection(slave), never()).commit();
    }

    @Test
    void nestedTransactions_popBoundManagersInLifoOrder() throws Exception {
        DynamicTransactionManager manager = new DynamicTransactionManager("master");
        DataSource master = mockDataSource("master");
        DataSource slave = mockDataSource("slave");
        manager.registerDataSource("master", master);
        manager.registerDataSource("slave", slave);

        DefaultTransactionDefinition requiresNew = new DefaultTransactionDefinition();
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        TransactionStatus masterStatus = manager.getTransaction(new DefaultTransactionDefinition());
        TransactionStatus slaveStatus = JpaPlusContext.withDS(
                "slave", (Callable<TransactionStatus>) () -> manager.getTransaction(requiresNew));

        manager.commit(slaveStatus);
        manager.commit(masterStatus);

        verify(connection(slave)).commit();
        verify(connection(master)).commit();
    }

    private static DataSource mockDataSource(String name) throws Exception {
        DataSource dataSource = mock(DataSource.class, name + "DataSource");
        Connection connection = mock(Connection.class, name + "Connection");
        when(connection.getAutoCommit()).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }

    private static Connection connection(DataSource dataSource) throws Exception {
        return dataSource.getConnection();
    }
}
