package io.github.guanxiangkai.web.plus.web.exception;

import java.io.Serial;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 导入数据校验异常，用于向前端返回可定位到行列的修正提示。
 */
public class ImportValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int MESSAGE_LIMIT = 20;

    private final List<ImportError> errors;

    public ImportValidationException(List<ImportError> errors) {
        super(buildMessage(errors));
        this.errors = List.copyOf(errors);
    }

    public List<ImportError> getErrors() {
        return errors;
    }

    private static String buildMessage(List<ImportError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "导入失败：数据格式不正确，请修改后重新导入";
        }
        String detail = errors.stream()
                .limit(MESSAGE_LIMIT)
                .map(ImportError::display)
                .collect(Collectors.joining("；"));
        if (errors.size() > MESSAGE_LIMIT) {
            detail += "；还有 " + (errors.size() - MESSAGE_LIMIT) + " 个问题未展示";
        }
        return "导入失败，请修改以下数据后重新导入：" + detail;
    }

    public record ImportError(int row, String column, String field, String message) {

        public String display() {
            String location = "第" + row + "行";
            if (column != null && !column.isBlank()) {
                location += "第" + column + "列";
            }
            return location + "：" + message;
        }
    }
}
