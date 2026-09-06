package io.github.guanxiangkai.redis.plus.queue.impl;

import io.github.guanxiangkai.redis.plus.core.async.RedisPlusAsyncExecutor;
import io.github.guanxiangkai.redis.plus.core.serializer.ValueSerializer;
import io.github.guanxiangkai.redis.plus.queue.QueueDelivery;
import io.github.guanxiangkai.redis.plus.queue.QueuePoisonMessage;
import io.github.guanxiangkai.redis.plus.queue.QueuePoisonReason;
import io.github.guanxiangkai.redis.plus.queue.QueueReadFailurePolicy;
import io.github.guanxiangkai.redis.plus.queue.QueueRuntimePolicy;
import io.github.guanxiangkai.redis.plus.queue.spi.DeadLetterHandler;
import io.github.guanxiangkai.redis.plus.queue.spi.PoisonMessageHandler;
import io.github.guanxiangkai.redis.plus.queue.spi.QueueRetryStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("NullAway")
class RedisStreamQueueTest {

    private StringRedisTemplate redisTemplate;
    private StreamOperations<String, Object, Object> streamOps;
    private ValueSerializer serializer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOps = mock(StreamOperations.class);
        serializer = mock(ValueSerializer.class);
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOps);
    }

    @Test
    void ensureGroupExists_bootstrapsMissingStream() {
        RecordId bootstrapId = RecordId.of("1-0");
        when(streamOps.createGroup("stream:orders", ReadOffset.from("0-0"), "group-a"))
                .thenThrow(new RuntimeException("ERR The XGROUP command requires the key to exist"))
                .thenReturn("OK");
        when(streamOps.add(anyMapRecord())).thenReturn(bootstrapId);

        new RedisStreamQueue<>(
                "orders", "group-a", "stream:", String.class, redisTemplate, serializer,
                null, null, QueueRuntimePolicy.defaults());

        verify(streamOps, times(2)).createGroup("stream:orders", ReadOffset.from("0-0"), "group-a");
        verify(streamOps).add(argThat(record ->
                "stream:orders".equals(record.getStream())
                        && "group-a".equals(record.getValue().get("__redis_plus_bootstrap__"))));
        verify(streamOps).delete("stream:orders", bootstrapId);
    }

    @Test
    void ensureGroupExists_busyGroupSkipsBootstrap() {
        when(streamOps.createGroup("stream:orders", ReadOffset.from("0-0"), "group-a"))
                .thenThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"));

        new RedisStreamQueue<>(
                "orders", "group-a", "stream:", String.class, redisTemplate, serializer,
                null, null, QueueRuntimePolicy.defaults());

        verify(streamOps, never()).add(anyMapRecord());
        verify(streamOps, never()).delete(anyString(), any(RecordId.class));
    }

    @Test
    void send_usesAtomicConditionalTrimScript() {
        when(streamOps.createGroup("stream:orders", ReadOffset.from("0-0"), "group-a")).thenReturn("OK");
        when(serializer.serialize("payload")).thenReturn("payload-json");
        when(streamOps.add(anyMapRecord())).thenReturn(RecordId.of("2-0"));
        when(redisTemplate.execute(anyRedisScript(), anyList(), any(), any())).thenReturn(0L);

        RedisStreamQueue<String> queue = new RedisStreamQueue<>(
                "orders", "group-a", "stream:", String.class, redisTemplate, serializer,
                null, null, new QueueRuntimePolicy(QueueRetryStrategy.noRetry(), DeadLetterHandler.logAndDiscard(),
                Duration.ofSeconds(1), 10, null, false, Duration.ofMinutes(5), 128,
                PoisonMessageHandler.logAndDiscard(), QueueReadFailurePolicy.defaults()));

        queue.send("payload");

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = redisScriptCaptor();
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of("stream:orders")), eq("group-a"), eq("128"));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("XPENDING"));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("XTRIM"));
        verify(streamOps, never()).trim(anyString(), anyLong(), anyBoolean());
    }

    @Test
    void malformedPayload_shouldQuarantineThenAcknowledge() {
        when(streamOps.createGroup("stream:orders", ReadOffset.from("0-0"), "group-a")).thenReturn("OK");
        when(serializer.deserialize("{broken", String.class))
                .thenThrow(new IllegalArgumentException("invalid json"));
        RecordId recordId = RecordId.of("3-0");
        when(streamOps.acknowledge("stream:orders", "group-a", recordId)).thenReturn(1L);
        PoisonMessageHandler poisonHandler = mock(PoisonMessageHandler.class);
        RedisStreamQueue<String> queue = streamQueue(poisonHandler);
        Consumer<String> consumer = mockConsumer();

        queue.processRecord(MapRecord.create("stream:orders", Map.<Object, Object>of("payload", "{broken"))
                .withId(recordId), consumer);

        ArgumentCaptor<QueuePoisonMessage> poisonCaptor = ArgumentCaptor.forClass(QueuePoisonMessage.class);
        verify(poisonHandler).handle(poisonCaptor.capture());
        assertEquals(QueuePoisonReason.DESERIALIZATION_FAILED, poisonCaptor.getValue().reason());
        assertEquals("{broken", poisonCaptor.getValue().rawPayload());
        verify(streamOps).acknowledge("stream:orders", "group-a", recordId);
        verifyNoInteractions(consumer);
    }

    @Test
    void quarantineFailure_shouldLeaveMalformedPayloadPending() {
        when(streamOps.createGroup("stream:orders", ReadOffset.from("0-0"), "group-a")).thenReturn("OK");
        when(serializer.deserialize("{broken", String.class))
                .thenThrow(new IllegalArgumentException("invalid json"));
        PoisonMessageHandler poisonHandler = mock(PoisonMessageHandler.class);
        doThrow(new IllegalStateException("quarantine unavailable"))
                .when(poisonHandler).handle(any(QueuePoisonMessage.class));
        RedisStreamQueue<String> queue = streamQueue(poisonHandler);

        queue.processRecord(MapRecord.create("stream:orders", Map.<Object, Object>of("payload", "{broken"))
                .withId(RecordId.of("4-0")), ignored -> {
                });

        verify(streamOps, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    void acknowledge_shouldRemainRetryableWhenFirstAckFails() {
        when(streamOps.createGroup("stream:orders", ReadOffset.from("0-0"), "group-a")).thenReturn("OK");
        RecordId recordId = RecordId.of("5-0");
        when(serializer.deserialize("payload-json", String.class)).thenReturn("payload");
        when(streamOps.read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(StreamReadOptions.class), anyStreamOffsets()))
                .thenReturn(List.of(record(recordId)));
        when(streamOps.acknowledge("stream:orders", "group-a", recordId))
                .thenThrow(new IllegalStateException("temporary Redis failure"))
                .thenReturn(1L);
        RedisStreamQueue<String> queue = streamQueue(PoisonMessageHandler.logAndDiscard());

        QueueDelivery<String> delivery = Objects.requireNonNull(queue.receive(Duration.ZERO));

        assertThrows(IllegalStateException.class, delivery::acknowledge);
        assertFalse(delivery.isAcknowledged());
        delivery.acknowledge();

        assertTrue(delivery.isAcknowledged());
        verify(streamOps, times(2)).acknowledge("stream:orders", "group-a", recordId);
    }

    @Test
    void acknowledge_shouldSerializeConcurrentCalls() throws Exception {
        when(streamOps.createGroup("stream:orders", ReadOffset.from("0-0"), "group-a")).thenReturn("OK");
        RecordId recordId = RecordId.of("6-0");
        when(serializer.deserialize("payload-json", String.class)).thenReturn("payload");
        when(streamOps.read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(StreamReadOptions.class), anyStreamOffsets()))
                .thenReturn(List.of(record(recordId)));
        CountDownLatch ackStarted = new CountDownLatch(1);
        CountDownLatch allowAck = new CountDownLatch(1);
        when(streamOps.acknowledge("stream:orders", "group-a", recordId)).thenAnswer(invocation -> {
            ackStarted.countDown();
            assertTrue(allowAck.await(5, TimeUnit.SECONDS));
            return 1L;
        });
        RedisStreamQueue<String> queue = streamQueue(PoisonMessageHandler.logAndDiscard());
        QueueDelivery<String> delivery = Objects.requireNonNull(queue.receive(Duration.ZERO));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(delivery::acknowledge);
            assertTrue(ackStarted.await(5, TimeUnit.SECONDS));
            assertFalse(delivery.isAcknowledged());
            var second = executor.submit(delivery::acknowledge);

            allowAck.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            allowAck.countDown();
            executor.shutdownNow();
        }

        assertTrue(delivery.isAcknowledged());
        verify(streamOps, times(1)).acknowledge("stream:orders", "group-a", recordId);
    }

    @Test
    void subscribe_shouldRestoreStoppedStateWhenExecutorRejectsTask() {
        when(streamOps.createGroup("stream:orders", ReadOffset.from("0-0"), "group-a")).thenReturn("OK");
        RedisPlusAsyncExecutor rejectedExecutor = mock(RedisPlusAsyncExecutor.class);
        doThrow(new RejectedExecutionException("saturated"))
                .doNothing()
                .doThrow(new RejectedExecutionException("saturated"))
                .doNothing()
                .when(rejectedExecutor).execute(anyString(), any(Runnable.class));
        RedisStreamQueue<String> streamQueue = new RedisStreamQueue<>(
                "orders", "group-a", "stream:", String.class, redisTemplate, serializer,
                null, rejectedExecutor, QueueRuntimePolicy.defaults());
        RedisListQueue<String> listQueue = new RedisListQueue<>(
                "orders", "list:", String.class, redisTemplate, serializer,
                rejectedExecutor, QueueRuntimePolicy.defaults());

        assertThrows(RejectedExecutionException.class, () -> streamQueue.subscribe(ignored -> {
        }));
        assertFalse(streamQueue.isRunning());
        assertTrue(streamQueue.subscribe(ignored -> {
        }).isRunning());
        streamQueue.stop();
        assertThrows(RejectedExecutionException.class, () -> listQueue.subscribe(ignored -> {
        }));
        assertFalse(listQueue.isRunning());
        assertTrue(listQueue.subscribe(ignored -> {
        }).isRunning());
        listQueue.stop();
    }

    private RedisStreamQueue<String> streamQueue(PoisonMessageHandler poisonHandler) {
        return new RedisStreamQueue<>(
                "orders", "group-a", "stream:", String.class, redisTemplate, serializer,
                null, mock(RedisPlusAsyncExecutor.class),
                new QueueRuntimePolicy(QueueRetryStrategy.noRetry(), DeadLetterHandler.logAndDiscard(),
                Duration.ofSeconds(1), 10, null, false, Duration.ofMinutes(5), 0,
                poisonHandler, QueueReadFailurePolicy.defaults()));
    }

    private static MapRecord<String, Object, Object> record(RecordId recordId) {
        return MapRecord.create("stream:orders", Map.<Object, Object>of("payload", "payload-json"))
                .withId(recordId);
    }

    @SuppressWarnings("unchecked")
    private static StreamOffset<String>[] anyStreamOffsets() {
        return any(StreamOffset[].class);
    }

    @SuppressWarnings("unchecked")
    private static <T> Consumer<T> mockConsumer() {
        return mock(Consumer.class);
    }

    @SuppressWarnings("unchecked")
    private static MapRecord<String, Object, Object> anyMapRecord() {
        return any(MapRecord.class);
    }

    @SuppressWarnings("unchecked")
    private static RedisScript<Long> anyRedisScript() {
        return any(DefaultRedisScript.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<RedisScript<Long>> redisScriptCaptor() {
        return ArgumentCaptor.forClass(RedisScript.class);
    }
}
