package io.github.guanxiangkai.web.plus.error.doc;

import io.github.guanxiangkai.web.plus.core.spi.ErrorCode;
import io.github.guanxiangkai.web.plus.error.enums.WebErrorCode;

import java.util.Arrays;
import java.util.List;

/**
 * Web Plus 框架内置错误码文档贡献者
 * <p>
 * 将 {@link WebErrorCode} 中定义的错误码自动输出到 API 文档中，
 * 由 {@code web-plus-doc} 的 {@code ErrorCodeOpenApiCustomizer} 汇总展示。
 * 此类可被业务侧禁用或替换，作为 {@code web-plus-error} 自动装配的一部分。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class WebErrorCodeContributor {

    /**
     * 将 {@link WebErrorCode} 枚举适配为 {@link ErrorCode} 列表
     */
    public List<ErrorCode> getErrorCodes() {
        return Arrays.stream(WebErrorCode.values())
                .<ErrorCode>map(e -> new ErrorCode() {
                    @Override
                    public String getCode() {
                        return e.getCode();
                    }

                    @Override
                    public String getMessage() {
                        return e.getMessage();
                    }

                    @Override
                    public int getHttpStatus() {
                        return e.getHttpStatus();
                    }
                })
                .toList();
    }

    public String getModuleName() {
        return "Web Plus 框架错误码";
    }
}

