package io.github.guanxiangkai.jpa.plus.field.sensitive.handler;

import io.github.guanxiangkai.jpa.plus.core.field.FieldHandler;
import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.field.sensitive.annotation.SensitiveWord;
import io.github.guanxiangkai.jpa.plus.field.sensitive.dfa.SensitiveWordEngine;
import io.github.guanxiangkai.jpa.plus.field.sensitive.exception.SensitiveWordException;
import io.github.guanxiangkai.jpa.plus.field.sensitive.spi.SensitiveStrategy;
import io.github.guanxiangkai.jpa.plus.field.sensitive.spi.SensitiveWordProvider;
import io.github.guanxiangkai.jpa.plus.field.sensitive.spi.SensitiveWordWhitelistProvider;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 敏感词检测处理器
 *
 * <p>实现 {@link FieldHandler}，在保存前检测 {@link SensitiveWord} 标注的字段。
 * 框架根据 classpath 自动选择检测引擎（houbb 优先，否则内置 DfaEngine）。</p>
 *
 * <h3>白名单支持</h3>
 * <p>实现 {@link SensitiveWordWhitelistProvider} 并注册为 Spring Bean，白名单中的词汇
 * 即使命中敏感词库也会被放行（不替换、不拒绝）。</p>
 *
 * <h3>热更新词库</h3>
 * <p>调用 {@link #refresh()} 会同时重新加载敏感词库和白名单，并重建引擎。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class SensitiveWordHandler implements FieldHandler {

    private static final ClassValue<SensitiveStrategy> CUSTOM_STRATEGY_CACHE = new ClassValue<>() {
        @Override
        protected SensitiveStrategy computeValue(Class<?> type) {
            return ReflectionUtils.instantiate(type.asSubclass(SensitiveStrategy.class));
        }
    };

    private final SensitiveWordProvider provider;
    private final SensitiveWordWhitelistProvider whitelistProvider;

    /**
     * 引擎工厂（words + whitelist → engine），BiFunction 保证白名单透明传递
     */
    private final BiFunction<Collection<String>, Collection<String>, SensitiveWordEngine> engineFactory;
    /**
     * 当前活跃引擎（volatile 保证 refresh() 后对所有线程立即可见）
     */
    private volatile SensitiveWordEngine engine;

    /**
     * 完整构造方法：带白名单支持
     *
     * @param provider          敏感词词库 SPI
     * @param whitelistProvider 白名单词库 SPI（可为 {@code null}）
     * @param engineFactory     引擎工厂（words, whitelist → engine）
     */
    public SensitiveWordHandler(SensitiveWordProvider provider,
                                SensitiveWordWhitelistProvider whitelistProvider,
                                BiFunction<Collection<String>, Collection<String>, SensitiveWordEngine> engineFactory) {
        this.provider = provider;
        this.whitelistProvider = whitelistProvider;
        this.engineFactory = engineFactory;
        this.engine = buildEngine();
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public boolean supports(Field field) {
        return field.isAnnotationPresent(SensitiveWord.class);
    }

    @Override
    public void beforeSave(Object entity, Field field) {
        try {
            Object value = ReflectionUtils.getFieldValue(entity, field);
            if (value instanceof String text) {
                SensitiveWord anno = field.getAnnotation(SensitiveWord.class);
                SensitiveStrategy strategy = resolveStrategy(anno);
                String result = strategy.handle(text, engine, anno.replacement());
                ReflectionUtils.setFieldValue(entity, field, result);
            }
        } catch (SensitiveWordException e) {
            throw e;
        } catch (Exception e) {
            log.error("[jpa-plus] 敏感词检测失败: field={}", field.getName(), e);
        }
    }

    /**
     * 热更新词库 —— 重新加载敏感词库和白名单，并重建检测引擎。
     *
     * <p>{@code volatile} 写保证更新对所有线程立即可见。
     * 更新期间正在执行的检测仍使用旧引擎，更新后的请求使用新引擎。</p>
     */
    public void refresh() {
        SensitiveWordEngine newEngine = buildEngine();
        this.engine = newEngine;
        log.info("[jpa-plus] SensitiveWordHandler refreshed — engine rebuilt with {} word(s).",
                newEngine.wordCount());
    }

    /**
     * 获取当前检测引擎（供外部查询，如管理接口）
     */
    public SensitiveWordEngine getEngine() {
        return engine;
    }

    // ─── 内部方法 ───

    private SensitiveWordEngine buildEngine() {
        try {
            Collection<String> words = provider.loadWords();
            Collection<String> whitelist = loadWhitelist();
            SensitiveWordEngine e = engineFactory.apply(words, whitelist);
            log.info("[jpa-plus] SensitiveWordHandler initialized — engine={}, wordCount={}, whitelistCount={}.",
                    e.getClass().getSimpleName(), e.wordCount(), whitelist.size());
            return e;
        } catch (Exception ex) {
            log.warn("[jpa-plus] Failed to build sensitive word engine, using empty engine: {}", ex.getMessage());
            return engineFactory.apply(null, List.of());
        }
    }

    private Collection<String> loadWhitelist() {
        if (whitelistProvider == null) return List.of();
        try {
            Collection<String> wl = whitelistProvider.loadWhitelist();
            return wl != null ? wl : List.of();
        } catch (Exception e) {
            log.warn("[jpa-plus] Failed to load sensitive word whitelist: {}", e.getMessage());
            return List.of();
        }
    }

    private SensitiveStrategy resolveStrategy(SensitiveWord annotation) {
        Class<? extends SensitiveStrategy> customClass = annotation.customStrategy();
        if (customClass != SensitiveStrategy.class) {
            return CUSTOM_STRATEGY_CACHE.get(customClass);
        }
        return annotation.strategy();
    }
}
