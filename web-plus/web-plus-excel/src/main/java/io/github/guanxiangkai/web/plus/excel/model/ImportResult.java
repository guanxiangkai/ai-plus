package io.github.guanxiangkai.web.plus.excel.model;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Excel 导入结果（Record）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@RegisterReflectionForBinding
public record ImportResult<T>(
        boolean success,
        int totalRows,
        int successRows,
        int failedRows,
        List<T> data,
        List<ImportError> errors
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ImportResult {
        if (data == null) data = List.of();
        if (errors == null) errors = List.of();
    }

    public static <T> ImportResult<T> success(List<T> data) {
        return new ImportResult<>(true, data.size(), data.size(), 0, List.copyOf(data), List.of());
    }

    public static <T> ImportResult<T> partial(List<T> data, List<ImportError> errors) {
        int total = data.size() + errors.size();
        return new ImportResult<>(false, total, data.size(), errors.size(), List.copyOf(data), List.copyOf(errors));
    }

    public static <T> ImportResult<T> failure(List<ImportError> errors) {
        return new ImportResult<>(false, errors.size(), 0, errors.size(), List.of(), List.copyOf(errors));
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @RegisterReflectionForBinding
    public record ImportError(
            int rowIndex,
            String column,
            String message,
            String value
    ) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public static ImportError of(int rowIndex, String column, String message) {
            return new ImportError(rowIndex, column, message, null);
        }

        public static ImportError of(int rowIndex, String column, String message, String value) {
            return new ImportError(rowIndex, column, message, value);
        }
    }
}

