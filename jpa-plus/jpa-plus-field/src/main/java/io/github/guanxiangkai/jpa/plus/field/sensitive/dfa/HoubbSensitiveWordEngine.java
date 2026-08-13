package io.github.guanxiangkai.jpa.plus.field.sensitive.dfa;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 基于 <a href="https://github.com/houbb/sensitive-word">houbb/sensitive-word</a> 的高级敏感词引擎（带白名单支持）
 *
 * <p>当 {@code com.github.houbb:sensitive-word} 存在于 classpath 时，框架自动优先使用此引擎。
 * 相比内置 {@link DfaEngine}，houbb 引擎额外支持：</p>
 *
 * <table border="1">
 *   <tr><th>能力</th><th>说明</th></tr>
 *   <tr><td>全角/半角</td><td>检测 ｓｅｘ 与 sex 为同一词</td></tr>
 *   <tr><td>繁简体转换</td><td>繁体字与简体字等价检测</td></tr>
 *   <tr><td>拼音绕过</td><td>检测 "shabi"、"sha bi" 等</td></tr>
 *   <tr><td>数字替换</td><td>检测 "s3x"、"sh*t" 等替换变体</td></tr>
 *   <tr><td>英文大小写</td><td>默认不区分大小写</td></tr>
 *   <tr><td>重复字</td><td>检测 "傻傻傻瓜" 等重复叠字</td></tr>
 * </table>
 *
 * <h3>引入 houbb 依赖</h3>
 * <pre>{@code
 * // Gradle
 * implementation("com.github.houbb:sensitive-word:0.29.5")
 *
 * // Maven
 * <dependency>
 *     <groupId>com.github.houbb</groupId>
 *     <artifactId>sensitive-word</artifactId>
 *     <version>0.29.5</version>
 * </dependency>
 * }</pre>
 *
 * <p><b>注意：</b>此类引用了 houbb 的编译期 API，不可在 houbb 不存在时被加载。
 * 框架通过 Spring {@code @ConditionalOnClass} 保证只有 houbb 在 classpath 时才创建此引擎。</p>
 *
 * @author guanxiangkai
 * @see DfaEngine
 * @see SensitiveWordEngine
 * @since 1.0.0
 */
@Slf4j
public class HoubbSensitiveWordEngine implements SensitiveWordEngine {

    private final SensitiveWordBs wordBs;
    private final int wordCount;

    /**
     * 使用用户提供的词库构建 houbb 引擎（无白名单）
     */
    public HoubbSensitiveWordEngine(Collection<String> words) {
        this(words, null);
    }

    /**
     * 使用用户提供的词库和白名单构建 houbb 引擎
     *
     * @param words     敏感词词库
     * @param whitelist 白名单词库（houbb 通过 {@code wordAllow} 实现放行）
     */
    public HoubbSensitiveWordEngine(Collection<String> words, Collection<String> whitelist) {
        List<String> wordList = words != null ? new ArrayList<>(words) : Collections.emptyList();
        List<String> whiteList = whitelist != null ? new ArrayList<>(whitelist) : Collections.emptyList();
        this.wordCount = (int) wordList.stream().filter(w -> w != null && !w.isBlank()).count();

        SensitiveWordBs bs = SensitiveWordBs.newInstance()
                .wordDeny(() -> wordList);
        if (!whiteList.isEmpty()) {
            bs = bs.wordAllow(() -> whiteList);
        }
        this.wordBs = bs.init();

        log.info("[jpa-plus] HoubbSensitiveWordEngine initialized with {} word(s), {} whitelist word(s) " +
                        "(full-width, pinyin, simplified/traditional Chinese support enabled).",
                wordCount, whiteList.size());
    }

    @Override
    public boolean contains(String text) {
        if (text == null || text.isEmpty()) return false;
        return wordBs.contains(text);
    }

    @Override
    public String replace(String text, String replacement) {
        if (text == null || text.isEmpty()) return text;
        // P0-16: An empty replacement string means "remove the sensitive word" (replace with "").
        // A null replacement defaults to "***". Never return unfiltered text when replacement is empty.
        String effectiveReplacement = replacement != null ? replacement : "***";

        List<String> hits = wordBs.findAll(text);
        if (hits.isEmpty()) return text;

        String result = text;
        for (String hit : hits.stream().distinct()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList()) {
            result = result.replace(hit, effectiveReplacement);
        }
        return result;
    }

    @Override
    public List<String> findAll(String text) {
        if (text == null || text.isEmpty()) return List.of();
        List<String> results = wordBs.findAll(text);
        return results != null ? List.copyOf(results) : List.of();
    }

    @Override
    public int wordCount() {
        return wordCount;
    }
}
