package io.github.guanxiangkai.web.plus.doc.customizer;

import io.github.guanxiangkai.web.plus.doc.spi.ErrorCodeDocumentContributor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.List;

/**
 * 错误码 OpenAPI 自定义器
 * <p>
 * 收集所有 {@link ErrorCodeDocumentContributor} 贡献的错误码，
 * 追加到 OpenAPI {@code description} 中，生成统一的错误码速查表。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@RequiredArgsConstructor
public class ErrorCodeOpenApiCustomizer implements WebPlusOpenApiCustomizer {

    private final List<ErrorCodeDocumentContributor> contributors;

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public void customize(OpenAPI openApi) {
        if (contributors == null || contributors.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        if (openApi.getInfo() != null && openApi.getInfo().getDescription() != null) {
            sb.append(openApi.getInfo().getDescription());
        }

        sb.append("\n\n## 错误码说明\n\n");

        contributors.stream()
                .sorted(Comparator.comparingInt(ErrorCodeDocumentContributor::getOrder))
                .forEach(contributor -> {
                    sb.append("### ").append(contributor.getModuleName()).append("\n\n");
                    sb.append("| 错误码 | 说明 | HTTP 状态 |\n");
                    sb.append("|--------|------|----------|\n");
                    contributor.getErrorCodes().forEach(code ->
                            sb.append("| `").append(code.getCode()).append("` | ")
                                    .append(code.getMessage()).append(" | ")
                                    .append(code.getHttpStatus()).append(" |\n")
                    );
                    sb.append("\n");
                });

        if (openApi.getInfo() == null) {
            openApi.setInfo(new Info());
        }
        openApi.getInfo().setDescription(sb.toString());
    }
}

