package io.github.guanxiangkai.jpa.plus.core.field;

import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 字段引擎
 *
 * <p>管理所有 {@link FieldHandler}，在实体保存前 / 查询后遍历字段并触发对应处理器。</p>
 *
 * <h3>批处理策略</h3>
 * <ul>
 *   <li><b>智能调度</b>：自动检测 Handler 是否实现批处理，优先调用批量 API</li>
 *   <li><b>字典翻译</b>：从 N 次查询优化为 1 次批量查询</li>
 *   <li><b>加密解密</b>：批量调用硬件加速（AES-NI）</li>
 *   <li><b>零开销兜底</b>：未实现批处理的 Handler 自动回退到逐个处理</li>
 * </ul>
 *
 * <h3>缓存优化</h3>
 * <ul>
 *   <li>{@code FIELD_CACHE} —— 每个实体类的字段列表，避免重复反射扫描</li>
 *   <li>{@code beforeSavePairsCache} / {@code afterQueryPairsCache} —— 预计算的
 *       (handler, field) 对，后续每次调用零 {@code supports()} 开销</li>
 * </ul>
 *
 * <h3>热重载钩子</h3>
 * <ul>
 *   <li>{@link #registerHandler(FieldHandler)} —— 运行时追加新 Handler</li>
 *   <li>{@link #unregisterHandler(Class)} —— 按类型移除 Handler</li>
 *   <li>{@link #clearHandlerCache()} —— 清除预计算缓存</li>
 * </ul>
 *
 * <p><b>设计模式：</b>
 * <ul>
 *   <li>组合模式（Composite） —— 聚合多个 Handler</li>
 *   <li>缓存模式（Cache） —— 多级缓存优化</li>
 *   <li>批处理模式（Batch Processing） —— 聚合操作减少 I/O</li>
 * </ul>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class FieldEngine {

    /**
     * 字段列表缓存（entityClass → 该类所有字段，含父类）。
     */
    private static final ClassValue<List<Field>> FIELD_CACHE = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            return ReflectionUtils.getHierarchyFields(type);
        }
    };

    private enum FieldOperation {
        BEFORE_SAVE("beforeSave", "beforeSaveBatch"),
        AFTER_QUERY("afterQuery", "afterQueryBatch");

        private final String singleLabel;
        private final String batchLabel;

        FieldOperation(String singleLabel, String batchLabel) {
            this.singleLabel = singleLabel;
            this.batchLabel = batchLabel;
        }
    }

    /**
     * beforeSave 处理器-字段对缓存。
     * 使用 AtomicReference 持有整个 ConcurrentHashMap，注册/注销 Handler 时原子替换整个 Map，
     * 避免 clear() 在锁外调用时与 computeIfAbsent 内部 bin 锁发生的竞态条件（stale cache）。
     */
    private final AtomicReference<ConcurrentHashMap<Class<?>, List<HandlerFieldPair>>> beforeSavePairsCacheRef
            = new AtomicReference<>(new ConcurrentHashMap<>());

    /**
     * afterQuery 处理器-字段对缓存（同 beforeSavePairsCacheRef）。
     */
    private final AtomicReference<ConcurrentHashMap<Class<?>, List<HandlerFieldPair>>> afterQueryPairsCacheRef
            = new AtomicReference<>(new ConcurrentHashMap<>());

    /**
     * 处理器列表读写锁（多读单写，提升并发读性能）
     */
    private final ReentrantReadWriteLock handlersLock = new ReentrantReadWriteLock();

    /**
     * 当前激活的处理器列表（已按 order 排序，由 handlersLock 保护）
     */
    private List<FieldHandler> handlers;

    public FieldEngine(List<FieldHandler> handlers) {
        this.handlers = sortedCopy(handlers);
        log.info("[jpa-plus] FieldEngine 初始化完成，已注册 {} 个字段处理器", this.handlers.size());
    }

    private static List<FieldHandler> sortedCopy(List<FieldHandler> source) {
        if (source == null || source.isEmpty()) return List.of();
        return source.stream()
                .sorted(Comparator.comparingInt(FieldHandler::order))
                .toList();
    }

    // ═══════════════════════════ 核心处理入口 ═══════════════════════════

    /**
     * 保存前处理单个实体（兜底方法，批量保存请使用 {@link #beforeSaveBatch}）
     *
     * @param entity      实体对象
     * @param entityClass 实体类型
     */
    public void beforeSave(Object entity, Class<?> entityClass) {
        processOne(entity, entityClass, FieldOperation.BEFORE_SAVE);
    }

    /**
     * 保存前批量处理
     *
     * <p><b>性能优势</b>：
     * <ul>
     *   <li>字典翻译：N 次查询 → 1 次查询（性能提升 ~10 倍）</li>
     *   <li>加密解密：批量调用硬件加速</li>
     *   <li>审计记录：批量插入审计日志</li>
     * </ul>
     * </p>
     *
     * @param entities    实体列表（非空）
     * @param entityClass 实体类型
     */
    public void beforeSaveBatch(List<?> entities, Class<?> entityClass) {
        processBatch(entities, entityClass, FieldOperation.BEFORE_SAVE);
    }

    /**
     * 查询后处理单个实体（兜底方法，批量查询请使用 {@link #afterQueryBatch}）
     *
     * @param entity      实体对象
     * @param entityClass 实体类型
     */
    public void afterQuery(Object entity, Class<?> entityClass) {
        processOne(entity, entityClass, FieldOperation.AFTER_QUERY);
    }

    private void processOne(Object entity, Class<?> entityClass, FieldOperation operation) {
        if (entity == null) return;
        List<HandlerFieldPair> pairs = getPairs(entityClass, operation);
        for (HandlerFieldPair pair : pairs) {
            try {
                if (operation == FieldOperation.BEFORE_SAVE) {
                    pair.handler().beforeSave(entity, pair.field());
                } else {
                    pair.handler().afterQuery(entity, pair.field());
                }
            } catch (Exception e) {
                log.error("[jpa-plus] FieldHandler {} 异常: handler={}, field={}",
                        operation.singleLabel,
                        pair.handler().getClass().getSimpleName(),
                        pair.field().getName(),
                        e);
                throw e;
            }
        }
    }

    /**
     * 查询后批量处理
     *
     * <p><b>典型场景</b>：列表查询时一次性翻译所有字典字段、解密敏感字段。</p>
     *
     * @param entities    实体列表（非空）
     * @param entityClass 实体类型
     */
    public void afterQueryBatch(List<?> entities, Class<?> entityClass) {
        processBatch(entities, entityClass, FieldOperation.AFTER_QUERY);
    }

    private void processBatch(List<?> entities, Class<?> entityClass, FieldOperation operation) {
        if (entities == null || entities.isEmpty()) return;

        List<HandlerFieldPair> pairs = getPairs(entityClass, operation);
        for (HandlerFieldPair pair : pairs) {
            FieldHandler handler = pair.handler();
            Field field = pair.field();
            try {
                if (handler instanceof BatchCapableFieldHandler batchHandler) {
                    if (operation == FieldOperation.BEFORE_SAVE) {
                        batchHandler.beforeSaveBatch(entities, field);
                    } else {
                        batchHandler.afterQueryBatch(entities, field);
                    }
                } else {
                    for (Object entity : entities) {
                        if (operation == FieldOperation.BEFORE_SAVE) {
                            handler.beforeSave(entity, field);
                        } else {
                            handler.afterQuery(entity, field);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[jpa-plus] FieldHandler {} 异常: handler={}, field={}, 实体数={}",
                        operation.batchLabel,
                        handler.getClass().getSimpleName(),
                        field.getName(),
                        entities.size(),
                        e);
                throw e;
            }
        }
    }

    private List<HandlerFieldPair> getPairs(Class<?> entityClass, FieldOperation operation) {
        return operation == FieldOperation.BEFORE_SAVE
                ? getBeforeSavePairs(entityClass)
                : getAfterQueryPairs(entityClass);
    }

    // ═══════════════════════════ 热重载钩子 ═══════════════════════════

    /**
     * 运行时追加新的 {@link FieldHandler}
     *
     * <p>追加后自动按 {@link FieldHandler#order()} 重新排序，并清除实体级预计算缓存。
     * 使用写锁保护，允许多线程并发读取（正常查询不受影响）。</p>
     *
     * @param handler 要注册的处理器，不能为 {@code null}
     */
    public void registerHandler(FieldHandler handler) {
        if (handler == null) throw new IllegalArgumentException("FieldHandler 不能为 null");
        handlersLock.writeLock().lock();
        try {
            List<FieldHandler> updated = new ArrayList<>(handlers);
            updated.add(handler);
            this.handlers = sortedCopy(updated);
            // Atomically swap to new empty maps inside the write lock to eliminate the race where a
            // thread snapshots old handlers, computes stale pairs, and stores them AFTER clear() runs.
            beforeSavePairsCacheRef.set(new ConcurrentHashMap<>());
            afterQueryPairsCacheRef.set(new ConcurrentHashMap<>());
        } finally {
            handlersLock.writeLock().unlock();
        }
        log.info("[jpa-plus] FieldEngine: 已注册 Handler '{}'，当前总数 {}",
                handler.getClass().getSimpleName(), getHandlerCount());
    }

    /**
     * 运行时按实现类型移除 {@link FieldHandler}
     *
     * <p>移除后清除实体级预计算缓存及该 Handler 的批处理能力缓存条目。
     * 若指定类型未注册，则静默忽略。</p>
     *
     * @param handlerClass 要移除的处理器类型
     */
    public void unregisterHandler(Class<? extends FieldHandler> handlerClass) {
        if (handlerClass == null) return;
        boolean removed;
        handlersLock.writeLock().lock();
        try {
            int before = handlers.size();
            this.handlers = handlers.stream()
                    .filter(h -> !handlerClass.isInstance(h))
                    .toList();
            removed = handlers.size() < before;
            if (removed) {
                beforeSavePairsCacheRef.set(new ConcurrentHashMap<>());
                afterQueryPairsCacheRef.set(new ConcurrentHashMap<>());
            }
        } finally {
            handlersLock.writeLock().unlock();
        }
        if (removed) {
            log.info("[jpa-plus] FieldEngine: 已注销 Handler '{}'，当前总数 {}",
                    handlerClass.getSimpleName(), getHandlerCount());
        } else {
            log.debug("[jpa-plus] FieldEngine: Handler '{}' 未找到，无需注销",
                    handlerClass.getSimpleName());
        }
    }

    /**
     * 清除预计算的处理器-字段对缓存（Handler 列表不变）
     *
     * <p>下次访问时自动针对各实体类重建缓存。
     * 字段列表缓存（{@code FIELD_CACHE}）不受影响，因为字段在运行时不会改变。</p>
     */
    public void clearHandlerCache() {
        beforeSavePairsCacheRef.set(new ConcurrentHashMap<>());
        afterQueryPairsCacheRef.set(new ConcurrentHashMap<>());
        log.debug("[jpa-plus] FieldEngine 缓存已清除（handler-field pairs）");
    }

    /**
     * 获取当前已注册的处理器数量
     *
     * @return 处理器数量
     */
    public int getHandlerCount() {
        handlersLock.readLock().lock();
        try {
            return handlers.size();
        } finally {
            handlersLock.readLock().unlock();
        }
    }

    /**
     * 获取当前已注册的处理器列表（防御性拷贝，按 order 排序）
     *
     * @return 不可变处理器列表快照
     */
    public List<FieldHandler> getHandlers() {
        handlersLock.readLock().lock();
        try {
            return List.copyOf(handlers);
        } finally {
            handlersLock.readLock().unlock();
        }
    }

    /**
     * P1-1 fix: The read lock is acquired BEFORE dereferencing the AtomicReference.
     * This prevents the ABA race where Thread A captures Map_old before registerHandler()
     * atomically swaps to Map_new; if Map_old already contained a cached entry for entityClass,
     * computeIfAbsent would return stale pairs without calling buildPairs().
     * With the read lock held, registerHandler()'s write lock cannot complete until we release,
     * guaranteeing that the map reference and the cached entry are consistent.
     */
    private List<HandlerFieldPair> getBeforeSavePairs(Class<?> entityClass) {
        handlersLock.readLock().lock();
        try {
            return beforeSavePairsCacheRef.get().computeIfAbsent(entityClass,
                    cls -> buildPairsUnderReadLock(cls, true));
        } finally {
            handlersLock.readLock().unlock();
        }
    }

    private List<HandlerFieldPair> getAfterQueryPairs(Class<?> entityClass) {
        handlersLock.readLock().lock();
        try {
            return afterQueryPairsCacheRef.get().computeIfAbsent(entityClass,
                    cls -> buildPairsUnderReadLock(cls, false));
        } finally {
            handlersLock.readLock().unlock();
        }
    }

    // ─── 工具方法 ────────────────────────────────────────────────────────────

    /**
     * Builds handler-field pairs. Caller MUST already hold at least the read lock on
     * {@code handlersLock} so that {@code this.handlers} is read atomically with the cache update.
     */
    private List<HandlerFieldPair> buildPairsUnderReadLock(Class<?> entityClass, boolean beforeSave) {
        List<Field> fields = FIELD_CACHE.get(entityClass);
        List<HandlerFieldPair> pairs = new ArrayList<>();
        for (FieldHandler handler : this.handlers) {
            for (Field field : fields) {
                if (handler.supports(field)) {
                    pairs.add(new HandlerFieldPair(handler, field));
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("[jpa-plus] FieldEngine cache built for {} ({}): {} pairs",
                    entityClass.getSimpleName(), beforeSave ? "beforeSave" : "afterQuery", pairs.size());
        }
        return List.copyOf(pairs);
    }

    // ─── 内部值对象 ──────────────────────────────────────────────────────────

    private record HandlerFieldPair(FieldHandler handler, Field field) {
    }
}
