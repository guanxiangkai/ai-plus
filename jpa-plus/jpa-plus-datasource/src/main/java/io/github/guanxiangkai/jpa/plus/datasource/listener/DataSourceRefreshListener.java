package io.github.guanxiangkai.jpa.plus.datasource.listener;

import io.github.guanxiangkai.jpa.plus.datasource.event.DataSourceChangeEvent;
import io.github.guanxiangkai.jpa.plus.datasource.registry.DynamicDataSourceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

/**
 * 数据源配置变更监听器
 *
 * <p>监听以下事件并触发数据源差异化刷新：</p>
 * <ul>
 *   <li>{@link DataSourceChangeEvent} —— 自定义数据源变更事件（编程式 / 配置中心触发）</li>
 * </ul>
 *
 * <h3>触发刷新的方式</h3>
 * <ol>
 *   <li><b>编程式触发</b> —— 注入 {@link io.github.guanxiangkai.jpa.plus.datasource.refresh.DataSourceRefresher}
 *       调用 {@code refresh()} 或直接发布 {@link DataSourceChangeEvent}</li>
 *   <li><b>定时触发</b> —— 配置 {@code spring.datasource.dynamic.refresh.enabled=true}
 *       开启定时轮询</li>
 *   <li><b>配置中心</b> —— Nacos / Apollo 等配置变更回调中发布 {@link DataSourceChangeEvent}</li>
 * </ol>
 *
 * <p><b>设计模式：</b>观察者模式（Observer） —— 使用 {@code @EventListener} 声明式监听，
 * 解耦变更源与刷新逻辑</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class DataSourceRefreshListener {

    private final DynamicDataSourceRegistry registry;

    /**
     * 收到数据源变更事件时重新加载数据源
     */
    @EventListener
    public void onDataSourceChange(DataSourceChangeEvent event) {
        log.info("Received DataSourceChangeEvent, reloading datasources...");
        registry.reload();
    }
}
