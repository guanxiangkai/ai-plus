package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.datasource.creator.DataSourceCreator;
import io.github.guanxiangkai.jpa.plus.datasource.model.DataSourceDefinition;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

/**
 * 基于 HikariCP 的数据源创建器
 *
 * <p>根据 {@link DataSourceDefinition} 创建 {@link HikariDataSource} 实例，
 * 完整映射所有连接池参数。HikariCP 是 Spring Boot 默认的连接池实现，具备极高性能和可靠性。</p>
 *
 * <h3>参数映射</h3>
 * <ul>
 *   <li>{@code jdbcUrl} ← {@link DataSourceDefinition#url()}</li>
 *   <li>{@code username / password} ← 同名字段</li>
 *   <li>{@code driverClassName} ← {@link DataSourceDefinition#driverClassName()}（可自动检测）</li>
 *   <li>{@code minimumIdle} ← {@link DataSourceDefinition#minimumIdle()}</li>
 *   <li>{@code maximumPoolSize} ← {@link DataSourceDefinition#maximumPoolSize()}</li>
 *   <li>{@code connectionTimeout} ← {@link DataSourceDefinition#connectionTimeout()}</li>
 *   <li>{@code idleTimeout} ← {@link DataSourceDefinition#idleTimeout()}</li>
 *   <li>{@code maxLifetime} ← {@link DataSourceDefinition#maxLifetime()}</li>
 *   <li>{@code poolName} ← {@link DataSourceDefinition#poolName()}</li>
 *   <li>{@code connectionTestQuery} ← {@link DataSourceDefinition#connectionTestQuery()}</li>
 * </ul>
 *
 * <p>用户可通过实现 {@link DataSourceCreator} 接口替换为 Druid / DBCP2 等其他连接池。</p>
 *
 * <p><b>设计模式：</b>工厂模式（Factory） —— 根据定义创建数据源实例</p>
 *
 * @author guanxiangkai
 * @see DataSourceCreator
 * @see DataSourceDefinition
 * @since 1.0.0
 */
@Slf4j
class HikariDataSourceCreator implements DataSourceCreator {

    @Override
    public DataSource createDataSource(DataSourceDefinition definition) {
        var config = new HikariConfig();
        config.setJdbcUrl(definition.url());
        config.setUsername(definition.username());
        config.setPassword(definition.password());

        // 驱动类名（由 DataSourceDefinition 自动推导或用户显式指定）
        if (definition.driverClassName() != null && !definition.driverClassName().isBlank()) {
            config.setDriverClassName(definition.driverClassName());
        }

        // 连接池参数
        config.setMinimumIdle(definition.minimumIdle());
        config.setMaximumPoolSize(definition.maximumPoolSize());
        config.setConnectionTimeout(definition.connectionTimeout());
        config.setIdleTimeout(definition.idleTimeout());
        config.setMaxLifetime(definition.maxLifetime());

        // 连接池名称
        config.setPoolName(definition.poolName());

        // 校验 SQL
        if (definition.connectionTestQuery() != null && !definition.connectionTestQuery().isBlank()) {
            config.setConnectionTestQuery(definition.connectionTestQuery());
        }

        // ─── HikariCP 扩展参数 ───
        if (definition.validationTimeout() > 0) {
            config.setValidationTimeout(definition.validationTimeout());
        }
        if (definition.leakDetectionThreshold() > 0) {
            config.setLeakDetectionThreshold(definition.leakDetectionThreshold());
        }
        config.setRegisterMbeans(definition.registerMbeans());

        log.debug("Creating HikariDataSource '{}' [{}] → {}",
                definition.name(),
                definition.dbType() != null ? definition.dbType().typeName() : "auto",
                definition.url());

        try {
            return new HikariDataSource(config);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to create HikariCP pool '" + definition.name() + "' for URL: " + definition.url() +
                            ". Cause: " + e.getMessage(), e);
        }
    }
}

