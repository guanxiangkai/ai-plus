package io.github.guanxiangkai.jpa.plus.field.sensitive.spi;

import java.util.Collection;

/**
 * 敏感词词库提供者 SPI 接口
 *
 * <p>用户实现此接口并注册为 Spring Bean，框架会在启动时调用 {@link #loadWords()}
 * 获取词库，然后使用内置 <b>DFA 引擎</b>（{@link io.github.guanxiangkai.jpa.plus.field.sensitive.dfa.DfaEngine}）
 * 自动构建检测树。<b>用户无需关心检测算法，只需提供词库数据。</b></p>
 *
 * <h3>实现示例</h3>
 *
 * <p><b>1. 静态词库（配置文件）：</b></p>
 * <pre>{@code
 * @Component
 * public class StaticWordProvider implements SensitiveWordProvider {
 *     @Override
 *     public Collection<String> loadWords() {
 *         return List.of("敏感词A", "违禁词B", "不当内容C");
 *     }
 * }
 * }</pre>
 *
 * <p><b>2. 数据库词库（动态加载）：</b></p>
 * <pre>{@code
 * @Component
 * public class DbWordProvider implements SensitiveWordProvider {
 *     private final SensitiveWordRepository repository;
 *
 *     @Override
 *     public Collection<String> loadWords() {
 *         return repository.findAllEnabledWords();
 *     }
 * }
 * }</pre>
 *
 * <p><b>3. 配置文件 + 数据库合并：</b></p>
 * <pre>{@code
 * @Component
 * public class MergedWordProvider implements SensitiveWordProvider {
 *     @Override
 *     public Collection<String> loadWords() {
 *         Set<String> words = new HashSet<>(defaultWords());
 *         words.addAll(dbWords());
 *         return words;
 *     }
 * }
 * }</pre>
 *
 * <h3>热更新词库</h3>
 * <p>若词库需要动态更新，注入 {@link io.github.guanxiangkai.jpa.plus.field.sensitive.handler.SensitiveWordHandler}
 * 并调用 {@code handler.refresh()} 即可触发重新加载：</p>
 * <pre>{@code
 * @Autowired
 * private SensitiveWordHandler sensitiveWordHandler;
 *
 * // 词库更新后调用（如 Nacos 配置推送、管理端操作等）
 * sensitiveWordHandler.refresh();
 * }</pre>
 *
 * <p><b>设计模式：</b>SPI 服务发现模式 + 策略模式</p>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.jpa.plus.field.sensitive.dfa.DfaEngine
 * @see io.github.guanxiangkai.jpa.plus.field.sensitive.handler.SensitiveWordHandler
 * @since 1.0.0
 */
@FunctionalInterface
public interface SensitiveWordProvider {

    /**
     * 加载敏感词词库
     *
     * <p>框架在启动时和每次 {@link io.github.guanxiangkai.jpa.plus.field.sensitive.handler.SensitiveWordHandler#refresh()}
     * 被调用时，会重新调用此方法并重建 DFA 引擎。
     * 实现类应保证幂等性，返回值可以是 {@code Set}、{@code List} 等任意 {@link Collection} 实现。</p>
     *
     * <p>返回 {@code null} 或空集合时，DFA 引擎不会命中任何词（相当于禁用敏感词功能）。</p>
     *
     * @return 敏感词集合（允许为 {@code null} 或空）
     */
    Collection<String> loadWords();
}
