package io.github.guanxiangkai.jpa.plus.datasource.routing;

import io.github.guanxiangkai.jpa.plus.datasource.context.JpaPlusContext;
import io.github.guanxiangkai.jpa.plus.datasource.enums.DsName;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;

/**
 * 动态路由数据源
 *
 * <p>继承 Spring 的 {@link AbstractRoutingDataSource}，
 * 通过 {@link JpaPlusContext#currentDS()} 获取当前线程/虚拟线程的数据源名称作为路由 key。</p>
 *
 * <h3>路由逻辑</h3>
 * <ol>
 *   <li>方法标注 {@code @DS("slave")} → AOP 创建 ScopedValue 作用域 → 路由到 slave</li>
 *   <li>方法未标注 {@code @DS} → ScopedValue 无绑定 → 默认路由到 primary 数据源</li>
 *   <li>{@code @DS("slave")} 方法执行完毕 → 退出 ScopedValue 作用域 → 自动恢复为 primary</li>
 * </ol>
 *
 * <h3>严格模式</h3>
 * <p>开启 {@code strict=true} 后，如果路由 key 不存在于目标数据源映射中，
 * 将抛出 {@link IllegalStateException} 而非回退到默认数据源。
 * 建议在生产环境开启，防止误路由导致数据写入错误的库。</p>
 *
 * <p><b>关键保证：</b>ScopedValue 是块作用域的，方法结束后自动恢复，
 * 不存在 ThreadLocal 忘记清理导致"粘连到从库"的问题。</p>
 *
 * @author guanxiangkai
 * @see JpaPlusContext
 * @since 1.0.0
 */
@Slf4j
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    /**
     * 主数据源名称（可配置，默认 "master"）
     */
    @Getter
    private final String primaryName;

    /**
     * 严格模式：路由 key 不存在时是否抛异常
     */
    private final boolean strict;

    /**
     * P1-3: Guards against calls to determineTargetDataSource() before initialize() has been invoked.
     * Volatile ensures the flag is immediately visible across threads when initialize() sets it.
     */
    private volatile boolean initialized = false;

    /**
     * 使用默认配置构造（primary="master", strict=false）
     */
    public DynamicRoutingDataSource() {
        this(DsName.MASTER, false);
    }

    /**
     * 使用指定的 primary 名称和 strict 模式构造
     *
     * @param primaryName 主数据源名称
     * @param strict      严格模式
     */
    public DynamicRoutingDataSource(String primaryName, boolean strict) {
        this.primaryName = primaryName != null ? primaryName : DsName.MASTER;
        this.strict = strict;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String ds = JpaPlusContext.currentDS(primaryName);
        if (log.isTraceEnabled()) {
            log.trace("DataSource routing → {}", ds);
        }
        return ds;
    }

    @Override
    protected DataSource determineTargetDataSource() {
        if (!initialized) {
            throw new IllegalStateException(
                    "DynamicRoutingDataSource has not been initialized yet. " +
                            "DynamicDataSourceRegistry.init() must complete before any datasource is accessed. " +
                            "If this error occurs during Spring context startup, check the bean initialization order.");
        }
        if (strict) {
            Object lookupKey = determineCurrentLookupKey();
            DataSource ds = getResolvedDataSources().get(lookupKey);
            if (ds == null) {
                throw new IllegalStateException(
                        "Cannot determine target DataSource for lookup key [" + lookupKey + "]. " +
                                "Strict mode is enabled — datasource '" + lookupKey + "' is not registered. " +
                                "Available datasources: " + getResolvedDataSources().keySet());
            }
            return ds;
        }
        return super.determineTargetDataSource();
    }

    /**
     * 延迟初始化 —— 由 {@link io.github.guanxiangkai.jpa.plus.datasource.registry.DynamicDataSourceRegistry}
     * 在注册完所有数据源后调用 {@link #initialize()} 完成实际初始化。
     *
     * <p>重写为空操作，避免 Spring 容器在目标数据源尚未就绪时提前调用导致异常。</p>
     */
    @Override
    public void afterPropertiesSet() {
        // No-op: deferred to initialize()
    }

    /**
     * 执行实际初始化（解析目标数据源映射）
     *
     * <p>由 {@link io.github.guanxiangkai.jpa.plus.datasource.registry.DynamicDataSourceRegistry#init()}
     * 在设置好所有目标数据源后调用。</p>
     */
    public void initialize() {
        super.initialize();
        this.initialized = true;
    }
}

