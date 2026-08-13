package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.datasource.creator.DataSourceCreator;
import io.github.guanxiangkai.jpa.plus.datasource.model.DataSourceDefinition;
import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * 基于 Alibaba Druid 的数据源创建器
 *
 * <p>当 classpath 中存在 {@code com.alibaba:druid} 且配置项
 * {@code spring.datasource.dynamic.pool-type=druid} 时，自动替换默认的 {@link HikariDataSourceCreator}。</p>
 *
 * <h3>Druid 优势</h3>
 * <ul>
 *   <li><b>SQL 监控</b> —— StatFilter 统计 SQL 执行频次、耗时、慢 SQL</li>
 *   <li><b>SQL 防火墙</b> —— WallFilter 防 SQL 注入</li>
 *   <li><b>Web 监控</b> —— /druid/ 内置控制台（需引入 {@code druid-spring-boot-starter}）</li>
 *   <li><b>扩展监控</b> —— 支持 Prometheus / Micrometer 指标导出</li>
 * </ul>
 *
 * <h3>参数映射</h3>
 * <table border="1">
 *   <tr><th>DataSourceDefinition</th><th>Druid</th><th>含义</th></tr>
 *   <tr><td>minimumIdle</td><td>initialSize / minIdle</td><td>最小/初始连接数</td></tr>
 *   <tr><td>maximumPoolSize</td><td>maxActive</td><td>最大连接数</td></tr>
 *   <tr><td>connectionTimeout</td><td>maxWait (ms)</td><td>获取连接超时</td></tr>
 *   <tr><td>idleTimeout</td><td>minEvictableIdleTimeMillis</td><td>空闲连接驱逐时间</td></tr>
 *   <tr><td>maxLifetime</td><td>maxEvictableIdleTimeMillis</td><td>连接最大存活时间</td></tr>
 *   <tr><td>connectionTestQuery</td><td>validationQuery</td><td>连接有效性检测 SQL</td></tr>
 *   <tr><td>poolName</td><td>name</td><td>连接池名称</td></tr>
 * </table>
 *
 * <p><b>设计模式：</b>工厂模式（Factory） —— 根据定义创建 Druid 数据源实例</p>
 *
 * @author guanxiangkai
 * @see HikariDataSourceCreator
 * @see DataSourceCreator
 * @since 1.0.0
 */
@Slf4j
class DruidDataSourceCreator implements DataSourceCreator {

    @Override
    public DataSource createDataSource(DataSourceDefinition definition) {
        DruidDataSource ds = new DruidDataSource();

        // ── 基础连接参数 ──
        ds.setName(definition.poolName());
        ds.setUrl(definition.url());
        ds.setUsername(definition.username());
        ds.setPassword(definition.password());

        if (definition.driverClassName() != null && !definition.driverClassName().isBlank()) {
            ds.setDriverClassName(definition.driverClassName());
        }

        // ── 连接池参数 ──
        ds.setInitialSize(definition.minimumIdle());
        ds.setMinIdle(definition.minimumIdle());
        ds.setMaxActive(definition.maximumPoolSize());
        ds.setMaxWait(definition.connectionTimeout());
        ds.setMinEvictableIdleTimeMillis(definition.idleTimeout());
        ds.setMaxEvictableIdleTimeMillis(definition.maxLifetime());

        // ── 连接有效性检测 ──
        if (definition.connectionTestQuery() != null && !definition.connectionTestQuery().isBlank()) {
            ds.setValidationQuery(definition.connectionTestQuery());
            ds.setTestWhileIdle(true);   // 空闲时异步检测，不影响获取连接性能
            ds.setTestOnBorrow(false);   // 借连接时不检测（性能优先）
            ds.setTestOnReturn(false);   // 归还时不检测
        }

        // ── 驱逐线程间隔 ──
        ds.setTimeBetweenEvictionRunsMillis(60_000L);  // 每 60s 运行一次空闲连接驱逐

        try {
            ds.init();
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to initialize Druid DataSource '" + definition.name() +
                            "' for URL [" + definition.url() + "]: " + e.getMessage(), e);
        }

        log.debug("[jpa-plus] Created DruidDataSource '{}' [{}] → {}",
                definition.name(),
                definition.dbType() != null ? definition.dbType().typeName() : "auto",
                definition.url());
        return ds;
    }
}


