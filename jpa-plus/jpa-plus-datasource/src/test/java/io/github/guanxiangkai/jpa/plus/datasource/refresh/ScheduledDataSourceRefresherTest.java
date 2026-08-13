package io.github.guanxiangkai.jpa.plus.datasource.refresh;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ScheduledDataSourceRefresherTest {

    @Test
    void constructor_rejectsZeroInterval() {
        DataSourceRefresher refresher = mock(DataSourceRefresher.class);

        assertThrows(IllegalArgumentException.class,
                () -> new ScheduledDataSourceRefresher(refresher, Duration.ZERO));
    }

    @Test
    void constructor_rejectsNegativeInterval() {
        DataSourceRefresher refresher = mock(DataSourceRefresher.class);

        assertThrows(IllegalArgumentException.class,
                () -> new ScheduledDataSourceRefresher(refresher, Duration.ofMillis(-1)));
    }
}
