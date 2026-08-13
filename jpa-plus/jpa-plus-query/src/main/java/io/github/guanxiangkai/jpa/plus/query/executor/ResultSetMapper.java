package io.github.guanxiangkai.jpa.plus.query.executor;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 结果集映射器接口
 *
 * @param <R> 映射目标类型
 */
@FunctionalInterface
public interface ResultSetMapper<R> {

    /**
     * 将 ResultSet 当前行映射为目标对象
     */
    R map(ResultSet rs, MappingContext context) throws SQLException;
}

