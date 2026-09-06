package io.github.guanxiangkai.jpa.plus.query.executor;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;
import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.core.util.NamingUtils;
import io.github.guanxiangkai.jpa.plus.query.context.OrderBy;
import io.github.guanxiangkai.jpa.plus.query.pagination.KeysetCursor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从结果行提取 Keyset 游标值。
 */
final class KeysetCursorExtractor {

    KeysetCursor buildNextCursor(Object lastRow, List<OrderBy> orderBys, int pageSize) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (OrderBy orderBy : orderBys) {
            String columnName = orderBy.column().columnName();
            String fieldName = NamingUtils.snakeToCamel(columnName);
            Field field = ReflectionUtils.findField(lastRow.getClass(), fieldName);
            if (field == null) {
                throw new JpaPlusException("Cannot extract keyset cursor field '" + fieldName
                        + "' from " + lastRow.getClass().getName());
            }
            Object value = ReflectionUtils.getFieldValue(lastRow, field);
            if (value == null) {
                throw new JpaPlusException("Keyset sort field must be non-null: " + columnName);
            }
            values.put(columnName, value);
        }
        return new KeysetCursor(values, pageSize);
    }
}
