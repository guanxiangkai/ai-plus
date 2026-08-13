package io.github.guanxiangkai.jpa.plus.query.compiler;

import java.util.Map;

/**
 * SQL 编译结果
 *
 * @param sql    SQL 语句（包含命名参数占位符 :paramName）
 * @param params 命名参数映射
 */
public record SqlResult(String sql, Map<String, Object> params) {

    /**
     * 防御性拷贝，保证 record 不可变
     */
    public SqlResult {
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}

