package io.github.guanxiangkai.jpa.plus.datasource.spi;

import javax.sql.DataSource;

/**
 * 数据源后置处理器（SPI 扩展点）
 *
 * <p>在数据源创建完成后对其进行包装或增强。框架在每个数据源由
 * {@link io.github.guanxiangkai.jpa.plus.datasource.creator.DataSourceCreator} 创建后，
 * 依次调用所有已注册的 {@code DataSourcePostProcessor}，用户可灵活地
 * 根据数据源名称、类型等条件决定是否代理。</p>
 *
 * <h3>核心用途</h3>
 * <ul>
 *   <li><b>分布式事务代理</b> —— Seata AT / XA 模式</li>
 *   <li><b>连接监控</b> —— 自定义连接池监控包装</li>
 *   <li><b>SQL 审计</b> —— datasource-proxy / Log4jdbc 等 JDBC 代理</li>
 * </ul>
 *
 * <h3>Seata 集成示例</h3>
 * <pre>{@code
 * @Component
 * public class SeataDataSourcePostProcessor implements DataSourcePostProcessor {
 *     @Override
 *     public DataSource postProcess(DataSource dataSource, String dataSourceName) {
 *         // 按需过滤：只对需要分布式事务的数据源进行代理
 *         if ("master".equals(dataSourceName) || "order_db".equals(dataSourceName)) {
 *             return new DataSourceProxy(dataSource);  // Seata AT
 *         }
 *         return dataSource;  // 其余数据源不代理
 *     }
 * }
 * }</pre>
 *
 * <p>多个处理器按 Spring {@code @Order} 或注册顺序依次执行，形成装饰链。</p>
 *
 * <p><b>设计模式：</b>装饰器模式（Decorator） —— 透明地增强数据源</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface DataSourcePostProcessor {

    /**
     * 对已创建的数据源进行后置处理
     *
     * <p>实现者可以返回原始数据源（不做处理），也可以返回包装后的代理数据源。
     * 返回值将替代原始数据源注册到路由表中。</p>
     *
     * @param dataSource     已创建的原始数据源
     * @param dataSourceName 数据源名称（路由 key，如 "master"、"slave_1"）
     * @return 处理后的数据源（不可为 null）
     */
    DataSource postProcess(DataSource dataSource, String dataSourceName);
}

