package io.github.guanxiangkai.jpa.plus.datasource.registry;

import io.github.guanxiangkai.jpa.plus.datasource.creator.DataSourceCreator;
import io.github.guanxiangkai.jpa.plus.datasource.model.DataSourceDefinition;
import io.github.guanxiangkai.jpa.plus.datasource.provider.DataSourceProvider;
import io.github.guanxiangkai.jpa.plus.datasource.routing.DynamicRoutingDataSource;
import io.github.guanxiangkai.jpa.plus.datasource.spi.DataSourcePostProcessor;
import io.github.guanxiangkai.jpa.plus.datasource.tx.DynamicTransactionManager;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 动态数据源注册中心
 *
 * <p>管理所有数据源的生命周期。通过 {@link DataSourceProvider} 获取配置列表，
 * 结合 {@link DataSourceCreator} 创建实例，经 {@link DataSourcePostProcessor} 链处理后，
 * 自动同步到 {@link DynamicRoutingDataSource} 和 {@link DynamicTransactionManager}。</p>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #init()} —— 启动时从 Provider 加载全部数据源（含 primary）</li>
 *   <li>{@link #reload()} —— 配置变更时差异化刷新（新增/更新/移除）</li>
 *   <li>{@link #add(DataSourceDefinition)} —— 运行时手动注册</li>
 *   <li>{@link #remove(String)} —— 运行时手动移除</li>
 *   <li>{@link #names()} —— 查看已注册的数据源名称</li>
 * </ul>
 *
 * <p><b>设计模式：</b>注册表模式（Registry） + 观察者模式（变更后通知路由数据源刷新）</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class DynamicDataSourceRegistry {

    private final DynamicRoutingDataSource routingDataSource;
    private final DataSourceCreator creator;
    private final DataSourceProvider provider;
    private final String primaryName;

    /**
     * 数据源后置处理器链（Seata 代理、监控包装等）
     */
    private final List<DataSourcePostProcessor> postProcessors;

    /**
     * 动态事务管理器（可选，为 null 时不联动事务管理器）
     */
    private final DynamicTransactionManager transactionManager;

    /**
     * 活跃数据源注册表
     */
    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();

    /**
     * 数据源定义快照（用于 reload 时判断配置是否变更）
     */
    private final Map<String, DataSourceDefinition> definitionMap = new ConcurrentHashMap<>();

    /**
     * 注册表写锁：保护复合读-改-写操作（init/reload/add/remove/destroy）。
     * 读路径（names/get）直接操作 ConcurrentHashMap，无需加锁。
     * DataSource 创建（JDBC connect + 连接池初始化）在写锁之外执行，避免长时间锁竞争。
     */
    private final ReentrantReadWriteLock registryLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = registryLock.writeLock();

    /**
     * 全参构造器
     *
     * @param routingDataSource  路由数据源
     * @param creator            数据源创建器
     * @param provider           数据源配置提供者
     * @param postProcessors     后置处理器链（可为空列表）
     * @param transactionManager 动态事务管理器（可为 null）
     */
    public DynamicDataSourceRegistry(DynamicRoutingDataSource routingDataSource,
                                     DataSourceCreator creator,
                                     DataSourceProvider provider,
                                     List<DataSourcePostProcessor> postProcessors,
                                     DynamicTransactionManager transactionManager) {
        this.routingDataSource = Objects.requireNonNull(routingDataSource);
        this.creator = Objects.requireNonNull(creator);
        this.provider = Objects.requireNonNull(provider);
        this.primaryName = routingDataSource.getPrimaryName();
        this.postProcessors = postProcessors != null ? List.copyOf(postProcessors) : List.of();
        this.transactionManager = transactionManager;
    }

    /**
     * 初始化 —— 从 Provider 加载全部数据源（启动时调用）
     */
    public void init() {
        List<DataSourceDefinition> definitions = provider.provide();
        if (definitions.isEmpty()) {
            throw new IllegalStateException(
                    "No datasource definitions provided! Check 'spring.datasource.dynamic.datasource' configuration.");
        }

        // 创建所有 DataSource（JDBC connect + 连接池初始化），在写锁外执行以避免长时间锁竞争
        Map<String, DataSource> created = new LinkedHashMap<>();
        for (DataSourceDefinition def : definitions) {
            try {
                created.put(def.name(), createAndPostProcess(def));
            } catch (Exception e) {
                created.values().forEach(this::closeQuietly);
                throw new IllegalStateException(
                        "Failed to create datasource '" + def.name() + "' (url=" + def.url() + "): " + e.getMessage(), e);
            }
        }

        writeLock.lock();
        try {
            for (DataSourceDefinition def : definitions) {
                DataSource ds = created.get(def.name());
                dataSourceMap.put(def.name(), ds);
                definitionMap.put(def.name(), def);
                registerTxManager(def.name(), ds);
            }
            if (!dataSourceMap.containsKey(primaryName)) {
                throw new IllegalStateException(
                        "Primary datasource '" + primaryName + "' not found in provider definitions! " +
                                "Available: " + dataSourceMap.keySet() +
                                ". Check 'spring.datasource.dynamic.primary' matches a configured datasource name.");
            }
            syncToRouting();
        } finally {
            writeLock.unlock();
        }
        log.info("DataSource registry initialized: {}", dataSourceMap.keySet());
    }

    /**
     * 重新加载 —— 从 Provider 获取最新配置，差异化刷新
     *
     * <p>对比当前快照，自动完成：
     * <ul>
     *   <li>新增 —— Provider 返回了新的数据源名称</li>
     *   <li>更新 —— 同名数据源的配置发生了变更</li>
     *   <li>移除 —— Provider 不再返回的数据源名称</li>
     * </ul>
     * DataSource 创建在写锁外执行；仅 map 更新和路由同步持有写锁。
     * </p>
     */
    public void reload() {
        List<DataSourceDefinition> latest = provider.provide();
        ChangeSet changeSet = calculateChangeSet(latest);
        if (changeSet.isEmpty()) {
            log.debug("DataSource reload: no changes detected");
            return;
        }

        // ② 在写锁外创建新 DataSource（JDBC connect 可能耗时数秒）
        Map<String, DataSource> created = new LinkedHashMap<>();
        Map<String, DataSource> refreshed = new LinkedHashMap<>();
        try {
            created.putAll(newDataSources(changeSet.toAdd()));
            refreshed.putAll(newDataSources(changeSet.toRefresh()));

            // ③ 写锁内完成 map 更新和路由同步（快速操作，持锁时间极短）
            writeLock.lock();
            try {
                for (DataSourceDefinition def : changeSet.toAdd()) {
                    DataSource ds = created.remove(def.name());
                    dataSourceMap.put(def.name(), ds);
                    definitionMap.put(def.name(), def);
                    registerTxManager(def.name(), ds);
                    log.info("DataSource '{}' added → {}", def.name(), def.url());
                }
                for (DataSourceDefinition def : changeSet.toRefresh()) {
                    DataSource old = dataSourceMap.get(def.name());
                    DataSource newDs = refreshed.remove(def.name());
                    dataSourceMap.put(def.name(), newDs);
                    definitionMap.put(def.name(), def);
                    registerTxManager(def.name(), newDs);
                    if (old != null) closeQuietly(old);
                    log.info("DataSource '{}' refreshed → {}", def.name(), def.url());
                }
                for (String name : changeSet.toRemove()) {
                    doRemove(name);
                    log.info("DataSource '{}' removed (no longer provided)", name);
                }
                syncToRouting();
            } finally {
                writeLock.unlock();
            }
        } catch (Exception e) {
            // 清理未成功应用的 DataSource
            created.values().forEach(this::closeQuietly);
            refreshed.values().forEach(this::closeQuietly);
            throw e;
        }
        log.info("DataSource reload complete: {}", dataSourceMap.keySet());
    }

    /**
     * 手动注册新数据源
     *
     * <p>DataSource 创建在写锁外执行，写锁内完成双重检查和 map 更新。</p>
     */
    public void add(DataSourceDefinition definition) {
        String name = definition.name();
        // 乐观预检（非阻塞）
        if (dataSourceMap.containsKey(name)) {
            throw new IllegalArgumentException("DataSource '" + name + "' already registered, " +
                    "use reload() or remove() first");
        }
        // 在写锁外创建（JDBC connect 可能耗时）
        DataSource ds = createAndPostProcess(definition);
        writeLock.lock();
        try {
            // 写锁内双重检查，防止并发 add() 竞态
            if (dataSourceMap.containsKey(name)) {
                closeQuietly(ds);
                throw new IllegalArgumentException("DataSource '" + name + "' already registered, " +
                        "use reload() or remove() first");
            }
            dataSourceMap.put(name, ds);
            definitionMap.put(name, definition);
            registerTxManager(name, ds);
            syncToRouting();
        } finally {
            writeLock.unlock();
        }
        log.info("DataSource '{}' registered → {}", name, definition.url());
    }

    /**
     * 手动移除数据源
     */
    public void remove(String name) {
        if (primaryName.equals(name)) {
            throw new IllegalArgumentException("Cannot remove primary datasource '" + primaryName + "'");
        }
        writeLock.lock();
        try {
            doRemove(name);
            syncToRouting();
        } finally {
            writeLock.unlock();
        }
        log.info("DataSource '{}' removed", name);
    }

    /**
     * 获取所有已注册的数据源名称
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(dataSourceMap.keySet());
    }

    /**
     * 获取指定数据源
     */
    public DataSource get(String name) {
        return dataSourceMap.get(name);
    }

    /**
     * 关闭所有数据源（应用关停时调用）
     */
    public void destroy() {
        writeLock.lock();
        try {
            dataSourceMap.forEach((name, ds) -> closeQuietly(ds));
            dataSourceMap.clear();
            definitionMap.clear();
        } finally {
            writeLock.unlock();
        }
        log.info("All datasources destroyed");
    }

    // ─── 内部方法 ───

    /**
     * 创建数据源并经过后置处理器链处理
     */
    private ChangeSet calculateChangeSet(List<DataSourceDefinition> latest) {
        Map<String, DataSourceDefinition> latestMap = latest.stream()
                .collect(Collectors.toMap(DataSourceDefinition::name, d -> d));

        List<DataSourceDefinition> toAdd = new ArrayList<>();
        List<DataSourceDefinition> toRefresh = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();

        for (DataSourceDefinition def : latest) {
            DataSourceDefinition existing = definitionMap.get(def.name());
            if (existing == null) {
                toAdd.add(def);
            } else if (!existing.equals(def)) {
                toRefresh.add(def);
            }
        }
        for (String name : definitionMap.keySet()) {
            if (!latestMap.containsKey(name)) {
                // Never remove the primary datasource via reload — a transient provider
                // failure must not cause a total outage.
                if (primaryName.equals(name)) {
                    log.warn("DataSource reload: primary datasource '{}' is absent from the latest provider result. " +
                            "Skipping removal to prevent total outage. Fix the provider configuration.", primaryName);
                    continue;
                }
                toRemove.add(name);
            }
        }
        return new ChangeSet(toAdd, toRefresh, toRemove);
    }

    private Map<String, DataSource> newDataSources(List<DataSourceDefinition> definitions) {
        Map<String, DataSource> created = new LinkedHashMap<>();
        for (DataSourceDefinition def : definitions) {
            created.put(def.name(), createAndPostProcess(def));
        }
        return created;
    }

    private DataSource createAndPostProcess(DataSourceDefinition def) {
        DataSource ds = creator.createDataSource(def);
        for (DataSourcePostProcessor processor : postProcessors) {
            ds = processor.postProcess(ds, def.name());
            Objects.requireNonNull(ds,
                    "DataSourcePostProcessor returned null for datasource '" + def.name() + "'");
        }
        return ds;
    }

    private void doRemove(String name) {
        DataSource removed = dataSourceMap.remove(name);
        definitionMap.remove(name);
        if (transactionManager != null) {
            transactionManager.removeDataSource(name);
        }
        if (removed != null) {
            closeQuietly(removed);
        }
    }

    private void registerTxManager(String name, DataSource ds) {
        if (transactionManager != null) {
            transactionManager.registerDataSource(name, ds);
        }
    }

    private void syncToRouting() {
        Map<Object, Object> targetMap = new HashMap<>(dataSourceMap);
        routingDataSource.setTargetDataSources(targetMap);

        DataSource primary = dataSourceMap.get(primaryName);
        if (primary != null) {
            routingDataSource.setDefaultTargetDataSource(primary);
        }

        routingDataSource.initialize();
    }

    private void closeQuietly(DataSource ds) {
        try {
            if (ds instanceof Closeable closeable) {
                closeable.close();
            } else if (ds instanceof AutoCloseable autoCloseable) {
                autoCloseable.close();
            }
        } catch (Exception e) {
            log.warn("Failed to close datasource: {}", e.getMessage());
        }
    }

    private record ChangeSet(List<DataSourceDefinition> toAdd,
                             List<DataSourceDefinition> toRefresh,
                             List<String> toRemove) {
        boolean isEmpty() {
            return toAdd.isEmpty() && toRefresh.isEmpty() && toRemove.isEmpty();
        }
    }
}
