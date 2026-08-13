package io.github.guanxiangkai.web.plus.protection.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * API 防抖配置属性
 * <p>
 * 对短时间内相同请求（相同用户 + 方法 + 路径）进行拦截，防止重复提交。
 * 底层依赖 Redis SET NX 实现分布式防抖窗口，仅在 Redis 可用时生效。
 * </p>
 *
 * <pre>
 * web-plus:
 *   debounce:
 *     enabled: true
 *     duration: 1s
 *     include-params: true
 * </pre>
 *
 * @param enabled       是否启用防抖（默认 true）
 * @param duration      防抖时间窗口（默认 1 秒）
 * @param includeParams 是否将查询参数纳入去重 key（默认 true）
 * @param excludePaths  不参与防抖的路径列表（支持 Ant 风格通配符）
 * @author guanxiangkai
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "web-plus.debounce")
public record DebounceProperties(
        Boolean enabled,
        Duration duration,
        Boolean includeParams,
        List<String> excludePaths
) {

    /**
     * 默认排除路径
     */
    private static final List<String> DEFAULT_EXCLUDE_PATHS = List.of(
            "/api/auth/**",
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/websocket/**"
    );

    public DebounceProperties {
        if (enabled == null) enabled = true;
        if (duration == null) duration = Duration.ofSeconds(1);
        if (includeParams == null) includeParams = true;
        if (excludePaths == null || excludePaths.isEmpty()) excludePaths = DEFAULT_EXCLUDE_PATHS;
    }
}

