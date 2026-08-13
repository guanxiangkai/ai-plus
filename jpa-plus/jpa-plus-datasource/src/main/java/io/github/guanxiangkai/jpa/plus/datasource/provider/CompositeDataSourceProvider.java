package io.github.guanxiangkai.jpa.plus.datasource.provider;

import io.github.guanxiangkai.jpa.plus.datasource.model.DataSourceDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * 组合数据源配置提供者
 *
 * <p>聚合多个 {@link DataSourceProvider}，合并所有 {@link DataSourceDefinition}。
 * 同名数据源以后注册的 Provider 为准（后覆盖前）。</p>
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>YAML 配置提供静态数据源 + JDBC 表提供动态数据源</li>
 *   <li>配置中心提供基础数据源 + 管理后台提供临时数据源</li>
 * </ul>
 *
 * <p><b>设计模式：</b>组合模式（Composite） —— 将多个 Provider 组合为统一接口</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class CompositeDataSourceProvider implements DataSourceProvider {

    private final List<DataSourceProvider> providers;

    /**
     * @param providers 有序 Provider 列表（后覆盖前）
     */
    public CompositeDataSourceProvider(List<DataSourceProvider> providers) {
        this.providers = Objects.requireNonNull(providers);
    }

    @Override
    public List<DataSourceDefinition> provide() {
        // 使用 LinkedHashMap 保序，同名后覆盖前
        var merged = new LinkedHashMap<String, DataSourceDefinition>();
        for (DataSourceProvider provider : providers) {
            for (DataSourceDefinition def : provider.provide()) {
                merged.put(def.name(), def);
            }
        }
        return List.copyOf(merged.values());
    }
}

