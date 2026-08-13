package io.github.guanxiangkai.jpa.plus.datasource.refresh;

import io.github.guanxiangkai.jpa.plus.datasource.registry.DynamicDataSourceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据源刷新门面
 *
 * <p>提供编程式数据源刷新能力。调用 {@link #refresh()} 方法即可触发
 * {@link DynamicDataSourceRegistry#reload()} 从配置源重新加载数据源配置。</p>
 *
 * <h3>使用示例</h3>
 *
 * <p><b>1. 注入后直接调用：</b></p>
 * <pre>{@code
 * @Autowired
 * private DataSourceRefresher refresher;
 *
 * // 在管理后台修改数据源配置后调用
 * public void reloadDataSources() {
 *     refresher.refresh();
 * }
 * }</pre>
 *
 * <p><b>2. 暴露为 REST 端点：</b></p>
 * <pre>{@code
 * @PostMapping("/datasource/refresh")
 * public ResponseEntity<Void> refreshDataSources() {
 *     refresher.refresh();
 *     return ResponseEntity.ok().build();
 * }
 * }</pre>
 *
 * <p><b>设计模式：</b>门面模式（Facade） —— 简化数据源刷新操作的调用入口</p>
 *
 * @author guanxiangkai
 * @see DynamicDataSourceRegistry#reload()
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class DataSourceRefresher {

    private final DynamicDataSourceRegistry registry;

    /**
     * 触发数据源配置重新加载
     *
     * <p>从 {@link io.github.guanxiangkai.jpa.plus.datasource.provider.DataSourceProvider}
     * 重新读取配置，差异化刷新数据源（新增 / 更新 / 移除）。</p>
     */
    public void refresh() {
        log.info("Datasource refresh triggered");
        registry.reload();
    }
}

