package io.github.guanxiangkai.jpa.plus.field.sensitive.dfa;

import java.util.*;

/**
 * 内置 DFA（确定有限状态自动机）敏感词引擎
 *
 * <p>基于前缀树（Trie）实现，时间复杂度 O(n·m)（n=文本长度，m=最长敏感词长度），
 * 远优于朴素逐词遍历的 O(n·k·m)（k=词库大小）。</p>
 *
 * <h3>核心特性</h3>
 * <ul>
 *   <li><b>最长匹配</b> —— 同一起点同时匹配多个词时，优先匹配最长的（如词库有"共产党"和"共产"，文本"共产党"命中"共产党"）</li>
 *   <li><b>大小写不敏感</b>（默认开启，可配置）</li>
 *   <li><b>线程安全</b> —— 引擎构建后只读，可被多线程并发访问</li>
 *   <li><b>热更新</b> —— 通过 {@link io.github.guanxiangkai.jpa.plus.field.sensitive.handler.SensitiveWordHandler#refresh()} 重建引擎</li>
 * </ul>
 *
 * <h3>算法说明</h3>
 * <pre>
 * 词库：["坏人", "坏蛋"]
 * Trie：
 *   root
 *    └─ '坏'
 *        ├─ '人'  [END] → "坏人"
 *        └─ '蛋'  [END] → "坏蛋"
 *
 * 检测 "这个坏人真坏蛋"：
 *   i=0 '这' → root 无子节点 → skip
 *   i=1 '个' → root 无子节点 → skip
 *   i=2 '坏' → root.children['坏'] → 继续
 *       j=3 '人' → isEnd=true → matchEnd=4（记录，继续尝试更长匹配）
 *       j=4 '真' → 节点不存在 → break
 *       → 命中"坏人"，替换 i[2,4)，i=4
 *   i=4 '真' → skip
 *   i=5 '坏' → '蛋' → isEnd → 命中"坏蛋"，i=7
 *   结果: "这个***真***"
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class DfaEngine implements SensitiveWordEngine {

    /**
     * 根节点，不代表任何字符
     */
    private final TrieNode root;

    /**
     * 是否忽略大小写（默认 true）
     */
    private final boolean ignoreCase;

    /**
     * 词库大小（用于日志和统计）
     */
    private final int wordCount;

    /**
     * 白名单集合（规范化后的词条），命中白名单的词直接放行
     */
    private final Set<String> whitelist;

    /**
     * 构建 DFA 引擎（默认忽略大小写，无白名单）
     */
    public DfaEngine(Collection<String> words) {
        this(words, true);
    }

    /**
     * 构建 DFA 引擎（无白名单）
     */
    public DfaEngine(Collection<String> words, boolean ignoreCase) {
        this(words, null, ignoreCase);
    }

    /**
     * 构建 DFA 引擎（带白名单，默认忽略大小写）
     *
     * @param words     敏感词集合
     * @param whitelist 白名单集合（命中白名单的词直接放行）
     */
    public DfaEngine(Collection<String> words, Collection<String> whitelist) {
        this(words, whitelist, true);
    }

    /**
     * 构建 DFA 引擎（完整参数）
     *
     * @param words      敏感词集合
     * @param whitelist  白名单集合（{@code null} 或空时无白名单效果）
     * @param ignoreCase 是否忽略大小写
     */
    public DfaEngine(Collection<String> words, Collection<String> whitelist, boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
        this.root = new TrieNode();
        int count = 0;
        if (words != null) {
            for (String word : words) {
                if (word != null && !word.isBlank()) {
                    insert(word);
                    count++;
                }
            }
        }
        this.wordCount = count;

        // 构建白名单（规范化，方便后续 O(1) 查找）
        if (whitelist != null && !whitelist.isEmpty()) {
            Set<String> wl = new HashSet<>();
            for (String w : whitelist) {
                if (w != null && !w.isBlank()) {
                    wl.add(normalize(w));
                }
            }
            this.whitelist = Set.copyOf(wl);
        } else {
            this.whitelist = Set.of();
        }
    }

    // ─── 公开 API ───

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /**
     * 检测文本中是否包含任意敏感词（白名单词跳过）
     */
    public boolean contains(String text) {
        if (isEmpty(text)) return false;
        String normalized = normalize(text);
        int len = normalized.length();
        for (int i = 0; i < len; i++) {
            int end = matchAt(normalized, i);
            if (end > i) {
                // 检查命中词是否在白名单中
                if (!whitelist.isEmpty() && whitelist.contains(normalized.substring(i, end))) {
                    i = end - 1; // 跳过本次命中（外层 for 会 i++）
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 将文本中所有敏感词替换为指定字符串（白名单词原样保留）
     */
    public String replace(String text, String replacement) {
        if (isEmpty(text)) return text;
        String normalized = normalize(text);
        int len = text.length();
        StringBuilder sb = new StringBuilder(len);
        int i = 0;
        while (i < len) {
            int end = matchAt(normalized, i);
            if (end > i) {
                // 检查是否在白名单中
                if (!whitelist.isEmpty() && whitelist.contains(normalized.substring(i, end))) {
                    // 白名单词原样保留，逐字符追加
                    sb.append(text, i, end);
                } else {
                    sb.append(replacement);
                }
                i = end;
            } else {
                sb.append(text.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * 查找文本中所有被命中的敏感词（去重，白名单词不计入结果）
     */
    public List<String> findAll(String text) {
        if (isEmpty(text)) return List.of();
        String normalized = normalize(text);
        int len = text.length();
        Set<String> seen = new LinkedHashSet<>();
        int i = 0;
        while (i < len) {
            int end = matchAt(normalized, i);
            if (end > i) {
                String matchedNorm = normalized.substring(i, end);
                if (whitelist.isEmpty() || !whitelist.contains(matchedNorm)) {
                    seen.add(text.substring(i, end)); // 原始大小写
                }
                i = end;
            } else {
                i++;
            }
        }
        return List.copyOf(seen);
    }

    /**
     * 返回词库中词条数量
     */
    public int wordCount() {
        return wordCount;
    }

    // ─── 内部方法 ───

    /**
     * 词库是否为空
     */
    public boolean isEmpty() {
        return wordCount == 0;
    }

    private void insert(String word) {
        TrieNode node = root;
        String normalized = normalize(word);
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.isEnd = true;
    }

    private int matchAt(String normalized, int start) {
        TrieNode node = root;
        int matchEnd = start;
        for (int j = start; j < normalized.length(); j++) {
            char c = normalized.charAt(j);
            node = node.children.get(c);
            if (node == null) break;
            if (node.isEnd) {
                matchEnd = j + 1;
            }
        }
        return matchEnd;
    }

    private String normalize(String s) {
        return ignoreCase ? s.toLowerCase(Locale.ROOT) : s;
    }

    // ─── 内部 Trie 节点 ───

    private static final class TrieNode {
        final Map<Character, TrieNode> children = new HashMap<>(4);
        boolean isEnd = false;
    }
}

