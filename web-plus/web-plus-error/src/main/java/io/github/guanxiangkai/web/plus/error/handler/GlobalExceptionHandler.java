package io.github.guanxiangkai.web.plus.error.handler;

import io.github.guanxiangkai.web.plus.core.exception.CoreBizException;
import io.github.guanxiangkai.web.plus.core.model.ApiResponse;
import io.github.guanxiangkai.web.plus.error.enums.WebErrorCode;
import io.github.guanxiangkai.web.plus.error.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器（WebFlux 响应式）
 * <p>
 * 责任链策略：按优先级匹配，最精确的异常类型优先处理。
 * 所有异常统一返回 {@link ApiResponse}。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Web Plus 自定义异常 ─────────────────────────────────────

    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuthException(AuthException ex, ServerWebExchange exchange) {
        log.warn("认证异常: code={}, path={}, msg={}", ex.getCode(), getPath(exchange), ex.getMessage());
        return ApiResponse.fail(ex.getHttpStatus(), ex.getMessage());
    }

    @ExceptionHandler(PermissionDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handlePermissionDenied(PermissionDeniedException ex, ServerWebExchange exchange) {
        log.warn("权限不足: path={}, msg={}", getPath(exchange), ex.getMessage());
        return ApiResponse.fail(ex.getHttpStatus(), ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> handleValidation(ValidationException ex, ServerWebExchange exchange) {
        log.debug("参数校验失败: path={}, msg={}", getPath(exchange), ex.getMessage());
        return ApiResponse.fail(ex.getHttpStatus(), ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex, ServerWebExchange exchange) {
        log.warn("业务异常: code={}, path={}, msg={}", ex.getCode(), getPath(exchange), ex.getMessage());
        ApiResponse<Void> body = ApiResponse.fail(ex.getHttpStatus(), ex.getMessage());
        return ResponseEntity.status(resolveHttpStatus(ex.getHttpStatus())).body(body);
    }

    @ExceptionHandler(CoreBizException.class)
    public ResponseEntity<ApiResponse<Void>> handleCoreBizException(CoreBizException ex, ServerWebExchange exchange) {
        log.warn("Core 业务异常: code={}, path={}, msg={}", ex.getCode(), getPath(exchange), ex.getMessage());
        ApiResponse<Void> body = ApiResponse.fail(ex.getHttpStatus(), ex.getMessage());
        return ResponseEntity.status(resolveHttpStatus(ex.getHttpStatus())).body(body);
    }

    @ExceptionHandler(RemoteTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public ApiResponse<Void> handleRemoteTimeout(RemoteTimeoutException ex, ServerWebExchange exchange) {
        log.warn("远程调用超时: code={}, path={}, msg={}", ex.getCode(), getPath(exchange), ex.getMessage());
        return ApiResponse.fail(ex.getHttpStatus(), ex.getMessage());
    }

    @ExceptionHandler(RemoteServiceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiResponse<Void> handleRemoteService(RemoteServiceException ex, ServerWebExchange exchange) {
        log.error("远程服务内部错误: code={}, path={}, msg={}", ex.getCode(), getPath(exchange), ex.getMessage());
        return ApiResponse.fail(ex.getHttpStatus(), ex.getMessage());
    }

    @ExceptionHandler(CircuitBreakerOpenException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleCircuitBreakerOpen(CircuitBreakerOpenException ex, ServerWebExchange exchange) {
        log.warn("服务熔断: code={}, path={}, msg={}", ex.getCode(), getPath(exchange), ex.getMessage());
        return ApiResponse.fail(ex.getHttpStatus(), ex.getMessage());
    }

    @ExceptionHandler(WebPlusException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebPlusException(WebPlusException ex, ServerWebExchange exchange) {
        log.warn("框架异常: code={}, path={}, msg={}", ex.getCode(), getPath(exchange), ex.getMessage());
        ApiResponse<Void> body = ApiResponse.fail(ex.getHttpStatus(), ex.getMessage());
        return ResponseEntity.status(resolveHttpStatus(ex.getHttpStatus())).body(body);
    }

    // ── Spring 参数校验异常 ────────────────────────────────────

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<List<String>> handleBindException(WebExchangeBindException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.toList());
        log.debug("参数绑定失败: {}", errors);
        return ApiResponse.fail(WebErrorCode.PARAM_INVALID.getHttpStatus(), "请求参数不合法", errors);
    }

    // ── Spring Web 内置异常 ────────────────────────────────────

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        log.debug("HTTP 状态异常: status={}, path={}", ex.getStatusCode(), getPath(exchange));
        ApiResponse<Void> body = ApiResponse.<Void>fail(
                ex.getStatusCode().value(),
                ex.getReason() != null ? ex.getReason() : ex.getMessage()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(MethodNotAllowedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleMethodNotAllowed(MethodNotAllowedException ex) {
        return ApiResponse.fail(WebErrorCode.REQUEST_METHOD_NOT_ALLOWED.getHttpStatus(),
                WebErrorCode.REQUEST_METHOD_NOT_ALLOWED.getMessage());
    }

    // ── 兜底异常 ──────────────────────────────────────────────

    @ExceptionHandler(Throwable.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleThrowable(Throwable ex, ServerWebExchange exchange) {
        log.error("系统异常: path={}, msg={}", getPath(exchange), ex.getMessage(), ex);
        return ApiResponse.fail(WebErrorCode.SYSTEM_ERROR.getHttpStatus(), "系统内部错误");
    }

    // ── 私有工具 ──────────────────────────────────────────────

    private String getPath(ServerWebExchange exchange) {
        try {
            return exchange.getRequest().getURI().getPath();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private HttpStatus resolveHttpStatus(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
