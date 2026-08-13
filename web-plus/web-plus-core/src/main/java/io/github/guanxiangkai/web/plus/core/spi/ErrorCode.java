package io.github.guanxiangkai.web.plus.core.spi;

/**
 * 错误码 SPI —— 统一错误码协议
 * <p>
 * 业务模块实现此接口，定义自己的错误码枚举。
 * 可与 {@code web-plus-doc} 的 {@code ErrorCodeDocumentContributor} 联动，
 * 自动将错误码输出到 OpenAPI 文档。
 * </p>
 *
 * <pre>
 * public enum BizErrorCode implements ErrorCode {
 *     ORDER_NOT_FOUND("BIZ_001", "订单不存在", 404),
 *     STOCK_NOT_ENOUGH("BIZ_002", "库存不足", 400);
 *
 *     ...
 * }
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface ErrorCode {

    /**
     * 错误码（如 A0001、B0002）
     */
    String getCode();

    /**
     * 错误描述信息
     */
    String getMessage();

    /**
     * 对应 HTTP 状态码，默认 400
     */
    default int getHttpStatus() {
        return 400;
    }
}

