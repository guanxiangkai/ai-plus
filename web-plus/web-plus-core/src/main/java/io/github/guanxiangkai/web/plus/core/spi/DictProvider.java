package io.github.guanxiangkai.web.plus.core.spi;

import io.github.guanxiangkai.web.plus.core.model.DictItem;

import java.util.List;

/**
 * 字典数据提供者 SPI
 * <p>
 * 业务侧实现此接口，按字典编码提供对应的字典项列表。
 * 框架在以下场景自动调用：
 * </p>
 * <ul>
 *   <li><b>L3 CacheLoader 回源</b>：当 L1（本地）+ L2（Redis）均未命中时，
 *       通过 {@code DictProvider} 从数据源加载并回填缓存</li>
 *   <li><b>Redis 变更监听</b>：当其他实例更新了 Redis 字典数据（发布 {@code dict:refresh} 事件），
 *       本实例收到通知后自动失效 L1 缓存，下次读取时触发 L3 → {@code DictProvider} 重新加载</li>
 * </ul>
 *
 * <h3>与 {@link DictWriter} 的区别</h3>
 * <ul>
 *   <li>{@code DictWriter} — <b>推模式</b>：启动时 / 手动刷新时批量推送字典到缓存</li>
 *   <li>{@code DictProvider} — <b>拉模式</b>：缓存未命中时按需加载单个字典类型</li>
 * </ul>
 * <p>两者可同时使用：{@code DictWriter} 负责启动预热，{@code DictProvider} 负责运行时兜底回源。</p>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * @Component
 * public class SysDictProvider implements DictProvider {
 *
 *     @Autowired
 *     private SysDictRepository repo;
 *
 *     @Override
 *     public List<DictItem> provide(String code) {
 *         return repo.findByType(code).stream()
 *             .map(d -> new DictItem(d.getValue(), d.getLabel()))
 *             .toList();
 *     }
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 * @see DictWriter
 * @see DictWriteSink
 */
@FunctionalInterface
public interface DictProvider {

    /**
     * 根据字典编码提供对应的字典项列表。
     * <p>
     * 若该编码不存在或无数据，返回空列表（不要返回 {@code null}）。
     * </p>
     *
     * @param code 字典类型标识，如 {@code "sys_status"}
     * @return 字典项列表，不可为 null
     */
    List<DictItem> provide(String code);
}

