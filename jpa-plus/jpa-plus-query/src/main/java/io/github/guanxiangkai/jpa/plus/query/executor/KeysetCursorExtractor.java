package io.github.guanxiangkai.jpa.plus.query.executor;

import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import io.github.guanxiangkai.jpa.plus.core.util.NamingUtils;
import io.github.guanxiangkai.jpa.plus.query.context.OrderBy;
import io.github.guanxiangkai.jpa.plus.query.pagination.KeysetCursor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts keyset cursor values from result rows.
 */
@Slf4j
final class KeysetCursorExtractor {

    KeysetCursor buildNextCursor(Object lastRow, List<OrderBy> orderBys, int pageSize) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (OrderBy orderBy : orderBys) {
            String columnName = orderBy.column().columnName();
            String fieldName = NamingUtils.snakeToCamel(columnName);
            Field field = ReflectionUtils.findField(lastRow.getClass(), fieldName);
            if (field == null) {
                log.debug("[jpa-plus] keyset: cannot find field '{}' on {}",
                        fieldName, lastRow.getClass().getSimpleName());
                continue;
            }
            try {
                values.put(columnName, field.get(lastRow));
            } catch (IllegalAccessException e) {
                log.debug("[jpa-plus] keyset: cannot extract field '{}' from {}",
                        fieldName, lastRow.getClass().getSimpleName());
            }
        }
        return new KeysetCursor(values, pageSize);
    }
}
