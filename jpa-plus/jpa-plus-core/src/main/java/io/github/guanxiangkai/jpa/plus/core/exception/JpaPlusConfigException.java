package io.github.guanxiangkai.jpa.plus.core.exception;

/**
 * 框架配置异常
 *
 * <p>在 Spring 应用启动阶段抛出，表示 JPA-Plus 配置不合法或缺失必要配置，
 * 属于不可恢复的致命错误。</p>
 *
 * <p>典型场景：缺少必填密钥、SPI 实现类冲突、配置属性超出范围。</p>
 */
public final class JpaPlusConfigException extends JpaPlusException {

    public JpaPlusConfigException(String message) {
        super(message);
    }

    public JpaPlusConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
