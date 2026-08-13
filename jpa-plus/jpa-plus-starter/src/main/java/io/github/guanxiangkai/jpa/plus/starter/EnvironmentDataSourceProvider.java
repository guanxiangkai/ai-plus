package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.datasource.enums.DatabaseType;
import io.github.guanxiangkai.jpa.plus.datasource.model.DataSourceDefinition;
import io.github.guanxiangkai.jpa.plus.datasource.provider.DataSourceProvider;
import io.github.guanxiangkai.jpa.plus.starter.DynamicDataSourceProperties.DataSourceItemProperties;
import io.github.guanxiangkai.jpa.plus.starter.DynamicDataSourceProperties.HikariProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Spring Environment 的数据源配置提供者
 *
 * <p>从 {@code spring.datasource.dynamic.datasource.*} 配置中读取数据源定义，
 * 每次调用 {@link #provide()} 都从 {@link Environment} 重新绑定，
 * 天然支持配置中心（Nacos / Apollo / Spring Cloud Config）的动态刷新。</p>
 *
 * <h3>合并规则</h3>
 * <p>全局 {@code spring.datasource.dynamic.hikari.*} 作为默认值，
 * 单数据源 {@code spring.datasource.dynamic.datasource.{name}.hikari.*} 覆盖全局。</p>
 *
 * <h3>自动检测</h3>
 * <p>框架根据 JDBC URL 自动检测 {@link DatabaseType}，
 * 用户无需手动指定 {@code driver-class-name}（也可显式指定以覆盖）。</p>
 *
 * <p><b>设计模式：</b>策略模式（Strategy） —— 作为 {@link DataSourceProvider} 的 YAML 实现</p>
 *
 * @author guanxiangkai
 * @see DynamicDataSourceProperties
 * @see DatabaseType
 * @since 1.0.0
 */
@Slf4j
class EnvironmentDataSourceProvider implements DataSourceProvider {

    private final Environment environment;

    /**
     * @param environment Spring Environment（配置中心刷新后内容自动更新）
     */
    EnvironmentDataSourceProvider(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    /**
     * 解析 Integer/Long 类型配置（局部 → 全局 → 默认值）
     */
    @SuppressWarnings("unchecked")
    private static <T extends Number> T resolve(HikariProperties local, HikariProperties global,
                                                java.util.function.Function<HikariProperties, T> getter,
                                                T defaultValue) {
        if (local != null) {
            T val = getter.apply(local);
            if (val != null) return val;
        }
        if (global != null) {
            T val = getter.apply(global);
            if (val != null) return val;
        }
        return defaultValue;
    }

    /**
     * 解析 String 类型配置（局部 → 全局 → null）
     */
    private static String resolveStr(HikariProperties local, HikariProperties global,
                                     java.util.function.Function<HikariProperties, String> getter) {
        if (local != null) {
            String val = getter.apply(local);
            if (val != null && !val.isBlank()) return val;
        }
        if (global != null) {
            String val = getter.apply(global);
            if (val != null && !val.isBlank()) return val;
        }
        return null;
    }

    // ─── 合并工具方法 ───

    /**
     * 解析 Boolean 类型配置（局部 → 全局 → 默认值）
     */
    private static boolean resolveBoolean(HikariProperties local, HikariProperties global,
                                          java.util.function.Function<HikariProperties, Boolean> getter,
                                          boolean defaultValue) {
        if (local != null) {
            Boolean val = getter.apply(local);
            if (val != null) return val;
        }
        if (global != null) {
            Boolean val = getter.apply(global);
            if (val != null) return val;
        }
        return defaultValue;
    }

    @Override
    public List<DataSourceDefinition> provide() {
        // 每次从 Environment 重新绑定，确保获取最新配置
        DynamicDataSourceProperties props = Binder.get(environment)
                .bind("spring.datasource.dynamic", DynamicDataSourceProperties.class)
                .orElseGet(DynamicDataSourceProperties::new);

        HikariProperties globalHikari = props.getHikari();
        List<DataSourceDefinition> definitions = new ArrayList<>();

        var datasourceMap = props.getDatasource();
        if (datasourceMap == null || datasourceMap.isEmpty()) {
            log.warn("No datasources found under 'spring.datasource.dynamic.datasource'");
            return List.of();
        }
        datasourceMap.forEach((name, item) -> {
            if (item.getUrl() == null || item.getUrl().isBlank()) {
                throw new IllegalArgumentException(
                        "Datasource '" + name + "' has no URL configured. " +
                                "Check 'spring.datasource.dynamic.datasource." + name + ".url'");
            }
            definitions.add(toDefinition(name, item, globalHikari));
        });

        log.debug("EnvironmentDataSourceProvider loaded {} datasource(s): {}",
                definitions.size(), definitions.stream().map(DataSourceDefinition::name).toList());

        return List.copyOf(definitions);
    }

    /**
     * 将单个数据源配置转换为 DataSourceDefinition，合并全局 + 局部 Hikari 配置
     */
    private DataSourceDefinition toDefinition(String name, DataSourceItemProperties item,
                                              HikariProperties globalHikari) {
        HikariProperties localHikari = item.getHikari();

        // 合并全局与局部 Hikari 配置（局部优先）
        int minimumIdle = resolve(localHikari, globalHikari, HikariProperties::getMinimumIdle, 5);
        int maximumPoolSize = resolve(localHikari, globalHikari, HikariProperties::getMaximumPoolSize, 10);
        long connectionTimeout = resolve(localHikari, globalHikari, HikariProperties::getConnectionTimeout, 30_000L);
        long idleTimeout = resolve(localHikari, globalHikari, HikariProperties::getIdleTimeout, 600_000L);
        long maxLifetime = resolve(localHikari, globalHikari, HikariProperties::getMaxLifetime, 1_800_000L);
        String poolName = resolveStr(localHikari, globalHikari, HikariProperties::getPoolName);
        String testQuery = resolveStr(localHikari, globalHikari, HikariProperties::getConnectionTestQuery);

        // HikariCP 扩展参数
        long validationTimeout = resolve(localHikari, globalHikari, HikariProperties::getValidationTimeout, 5_000L);
        long leakDetectionThreshold = resolve(localHikari, globalHikari, HikariProperties::getLeakDetectionThreshold, 0L);
        boolean registerMbeans = resolveBoolean(localHikari, globalHikari, HikariProperties::getRegisterMbeans, false);

        // 自动检测 DatabaseType
        var dbType = DatabaseType.fromUrl(item.getUrl())
                .or(() -> DatabaseType.fromDriverClassName(item.getDriverClassName()))
                .orElse(null);

        return new DataSourceDefinition(
                name,
                dbType,
                item.getUrl(),
                item.getUsername(),
                item.getPassword(),
                item.getDriverClassName(),
                minimumIdle,
                maximumPoolSize,
                connectionTimeout,
                idleTimeout,
                maxLifetime,
                poolName,
                testQuery,
                validationTimeout,
                leakDetectionThreshold,
                registerMbeans
        );
    }
}


