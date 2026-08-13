package io.github.guanxiangkai.jpa.plus.core.field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FieldEngineTest {

    private FieldEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FieldEngine(List.of(new StampHandler(), new UpperCaseHandler()));
    }

    @Test
    void singleEntity_allHandlersApplied() {
        var entity = new SampleEntity();
        engine.beforeSave(entity, SampleEntity.class);
        assertThat(entity.stamp).isEqualTo("STAMPED");
        assertThat(entity.name).isEqualTo("HELLO");
    }

    @Test
    void batchEntities_allProcessed() {
        var e1 = new SampleEntity();
        var e2 = new SampleEntity();
        engine.beforeSaveBatch(List.of(e1, e2), SampleEntity.class);
        assertThat(e1.stamp).isEqualTo("STAMPED");
        assertThat(e2.stamp).isEqualTo("STAMPED");
    }

    @Test
    void nullEntity_skippedGracefully() {
        // should not throw
        engine.beforeSave(null, SampleEntity.class);
    }

    @Test
    void registerHandler_appearsInHandlers() {
        var extra = new StampHandler();
        engine.registerHandler(extra);
        assertThat(engine.getHandlerCount()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void unregisterHandler_removedFromHandlers() {
        engine.unregisterHandler(StampHandler.class);
        var entity = new SampleEntity();
        engine.beforeSave(entity, SampleEntity.class);
        assertThat(entity.stamp).isNull();
    }

    @Test
    void concurrentRegistration_threadSafe() throws InterruptedException {
        int threads = 20;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        engine.registerHandler(new StampHandler());
                        engine.beforeSave(new SampleEntity(), SampleEntity.class);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        latch.await();
        assertThat(errors.get()).isZero();
    }

    // ── Fixtures ──

    static class SampleEntity {
        String stamp;
        String name = "hello";
    }

    static class StampHandler implements FieldHandler {
        @Override
        public int order() {
            return 10;
        }

        @Override
        public boolean supports(Field field) {
            return field.getName().equals("stamp");
        }

        @Override
        public void beforeSave(Object entity, Field field) {
            try {
                field.setAccessible(true);
                field.set(entity, "STAMPED");
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class UpperCaseHandler implements FieldHandler {
        @Override
        public int order() {
            return 20;
        }

        @Override
        public boolean supports(Field field) {
            return field.getName().equals("name");
        }

        @Override
        public void beforeSave(Object entity, Field field) {
            try {
                field.setAccessible(true);
                Object v = field.get(entity);
                if (v instanceof String s) field.set(entity, s.toUpperCase());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
