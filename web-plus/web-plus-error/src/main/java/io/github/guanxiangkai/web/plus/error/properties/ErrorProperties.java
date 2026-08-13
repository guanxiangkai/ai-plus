package io.github.guanxiangkai.web.plus.error.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * web-plus-error 配置属性
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "web-plus.error")
public record ErrorProperties(
        Boolean includeStacktrace,
        String bizHttpStatusMode
) {
    public ErrorProperties {
        if (includeStacktrace == null) includeStacktrace = false;
        if (bizHttpStatusMode == null) bizHttpStatusMode = "BODY";
    }
}

