package io.github.guanxiangkai.jpa.plus.datasource.event;

import org.springframework.context.ApplicationEvent;

/**
 * 数据源变更事件
 *
 * <p>当外部系统（如配置中心、管理后台）修改了数据源配置后，
 * 通过 Spring 事件机制发布此事件，触发框架重新加载数据源配置。</p>
 *
 * <h3>触发方式</h3>
 *
 * <p><b>1. 通过 {@code DataSourceRefresher}（推荐）：</b></p>
 * <pre>{@code
 * @Autowired
 * private DataSourceRefresher refresher;
 *
 * public void onConfigChanged() {
 *     refresher.refresh();
 * }
 * }</pre>
 *
 * <p><b>2. 通过 Spring 事件发布：</b></p>
 * <pre>{@code
 * @Autowired
 * private ApplicationEventPublisher publisher;
 *
 * public void onConfigChanged() {
 *     publisher.publishEvent(new DataSourceChangeEvent(this));
 * }
 * }</pre>
 *
 * <p><b>3. 配合配置中心（Nacos / Apollo 等）：</b></p>
 * <pre>{@code
 * @NacosConfigListener(dataId = "datasource.yaml")
 * public void onNacosChange(String config) {
 *     refresher.refresh();
 * }
 * }</pre>
 *
 * <p><b>设计模式：</b>观察者模式（Observer） —— 解耦配置变更源与数据源刷新逻辑</p>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.jpa.plus.datasource.registry.DynamicDataSourceRegistry
 * @see io.github.guanxiangkai.jpa.plus.datasource.listener.DataSourceRefreshListener
 * @since 1.0.0
 */
public class DataSourceChangeEvent extends ApplicationEvent {

    /**
     * 创建数据源变更事件
     *
     * @param source 事件源（通常为触发变更的 Bean）
     */
    public DataSourceChangeEvent(Object source) {
        super(source);
    }
}

