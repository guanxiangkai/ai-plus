package io.github.guanxiangkai.web.plus.doc.spi;

import io.github.guanxiangkai.web.plus.core.spi.ErrorCode;

import java.util.List;

/**
 * 错误码文档贡献者 SPI
 * <p>
 * 业务模块实现此接口，向接口文档注入自定义错误码说明。
 * {@code web-plus-doc} 在构建 OpenAPI 时会自动汇总所有实现，
 * 生成统一的错误码说明页面或接口 description 补充。
 * </p>
 *
 * <pre>
 * &#64;Component
 * public class BizErrorCodeContributor implements ErrorCodeDocumentContributor {
 *     &#64;Override
 *     public List&lt;ErrorCode&gt; getErrorCodes() {
 *         return Arrays.asList(BizErrorCode.values());
 *     }
 *
 *     &#64;Override
 *     public String getModuleName() { return "订单模块"; }
 * }
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface ErrorCodeDocumentContributor {

    /**
     * 返回该模块所有错误码
     */
    List<? extends ErrorCode> getErrorCodes();

    /**
     * 模块名称（用于文档分组标题）
     */
    default String getModuleName() {
        return "业务错误码";
    }

    /**
     * 优先级，数字越小越靠前
     */
    default int getOrder() {
        return 100;
    }
}

