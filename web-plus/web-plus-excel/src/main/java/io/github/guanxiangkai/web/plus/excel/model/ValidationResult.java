package io.github.guanxiangkai.web.plus.excel.model;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.io.Serial;
import java.io.Serializable;

/**
 * Excel 验证结果（Record）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@RegisterReflectionForBinding
public record ValidationResult(
        boolean valid,
        String message,
        String fileName,
        long fileSize,
        String fileType
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static ValidationResult success(String fileName, long fileSize, String fileType) {
        return new ValidationResult(true, "验证通过", fileName, fileSize, fileType);
    }

    public static ValidationResult failure(String message) {
        return new ValidationResult(false, message, null, 0, null);
    }

    public boolean isFailed() {
        return !valid;
    }
}

