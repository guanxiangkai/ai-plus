package io.github.guanxiangkai.web.plus.core.spi;

import io.github.guanxiangkai.web.plus.core.model.DictItem;

import java.util.List;
import java.util.Map;

/**
 * 字典写入 SPI
 * <p>
 * 业务服务实现此接口，负责将字典数据写入框架提供的存储。
 * web-plus 在应用启动完成（{@code ApplicationReadyEvent}）后，依次调用所有已注册的
 * {@code DictWriter} Bean，将字典数据初始化到字典存储；
 * 业务侧也可注入 {@code DictRefresher} 随时触发刷新。
 * </p>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * // 业务服务实现 DictWriter，把字典数据推入 sink
 * @Component
 * public class SysDictWriter implements DictWriter {
 *
 *     @Autowired
 *     private SysDictRepository repo;
 *
 *     @Override
 *     public void write(DictWriteSink sink) {
 *         // 先查询类型，再按单个类型读取完整且有序的数据，避免无界全表扫描。
 *         for (String type : repo.findEnabledTypes()) {
 *             sink.put(type, repo.findByTypeAndEnabledTrueOrderBySortOrderAsc(type).stream()
 *                     .map(d -> new DictItem(d.getValue(), d.getLabel()))
 *                     .toList());
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 * @see DictWriteSink
 */
@FunctionalInterface
public interface DictWriter {

    /**
     * 将字典数据写入存储。
     * <p>
     * 框架会在方法调用时提供 {@link DictWriteSink}，业务侧只需按类型调用
     * {@link DictWriteSink#put(String, List)}。
     * </p>
     *
     * @param sink 字典写入端点，由框架提供，调用 {@code put(type, items)} 写入每种字典
     */
    void write(DictWriteSink sink);

    /**
     * 便捷方法：一次性写入多个字典类型。
     * <p>
     * 默认实现遍历 {@code all} 中的每个条目并依次调用 {@link DictWriteSink#put}。
     * 若子类有更高效的批量写入方式可覆盖此方法。
     * </p>
     *
     * @param sink 字典写入端点
     * @param all  字典类型 → 字典项列表 的映射
     */
    default void writeAll(DictWriteSink sink, Map<String, List<DictItem>> all) {
        all.forEach(sink::put);
    }
}
