package io.github.guanxiangkai.web.plus.doc.customizer;

import io.swagger.v3.oas.models.OpenAPI;

/**
 * OpenAPI 自定义器 SPI —— 策略模式，支持多个自定义器按 order 排序执行
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface WebPlusOpenApiCustomizer {

    /**
     * 自定义 OpenAPI 对象
     *
     * @param openApi 待修改的 OpenAPI 对象
     */
    void customize(OpenAPI openApi);

    /**
     * 执行优先级，数字越小越先执行
     */
    default int getOrder() {
        return 0;
    }
}

