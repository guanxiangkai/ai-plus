package io.github.guanxiangkai.jpa.plus.query.executor;

import java.util.Map;

/**
 * 结果集映射上下文
 *
 * @param columnIndex 列名 → 列索引映射
 * @param aliasMap    列别名映射
 */
public record MappingContext(
        Map<String, Integer> columnIndex,
        Map<String, String> aliasMap
) {
}

