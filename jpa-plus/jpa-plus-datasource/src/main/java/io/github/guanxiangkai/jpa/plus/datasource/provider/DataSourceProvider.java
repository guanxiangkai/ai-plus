package io.github.guanxiangkai.jpa.plus.datasource.provider;

import io.github.guanxiangkai.jpa.plus.datasource.model.DataSourceDefinition;

import java.util.List;

/**
 * 数据源配置提供者（SPI）
 *
 * <p>框架通过此接口获取数据源配置列表，不关心数据来源 ——
 * 可以从 YAML、数据库、Nacos、Apollo、Consul 等任意方式加载。</p>
 *
 * <h3>内置实现</h3>
 * <ul>
 *   <li>{@code EnvironmentDataSourceProvider} —— 从 {@code spring.datasource.dynamic.datasource.*} 读取（默认）</li>
 *   <li>{@link JdbcDataSourceProvider} —— 从数据库配置表读取（可选）</li>
 *   <li>{@link CompositeDataSourceProvider} —— 组合多个 Provider</li>
 * </ul>
 *
 * <h3>自定义示例</h3>
 * <pre>{@code
 * @Component
 * public class NacosDataSourceProvider implements DataSourceProvider {
 *
 *     @Override
 *     public List<DataSourceDefinition> provide() {
 *         return List.of(
 *             DataSourceDefinition.of("slave", slaveUrl, user, pwd),
 *             DataSourceDefinition.of("pg", pgUrl, pgUser, pgPwd)
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><b>设计模式：</b>策略接口模式（Strategy） —— 解耦数据源配置获取与数据源创建</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface DataSourceProvider {

    /**
     * 提供数据源配置列表
     *
     * <p>返回的列表应包含所有需要注册的数据源（含 primary）。
     * 框架会对比当前注册表，自动完成新增、更新、移除。</p>
     *
     * @return 数据源定义列表（不可为 null）
     */
    List<DataSourceDefinition> provide();
}
