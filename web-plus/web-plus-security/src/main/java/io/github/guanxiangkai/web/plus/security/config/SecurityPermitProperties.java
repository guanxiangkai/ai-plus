package io.github.guanxiangkai.web.plus.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Security 额外放行路径配置
 * <p>
 * 各业务服务可通过 {@code web-plus.security.permit-patterns} 配置额外的免认证路径，
 * 无需单独创建 {@code SecurityWebFilterChain} bean，避免多链冲突。
 * </p>
 *
 * <pre>
 * # application.yml 示例
 * web-plus:
 *   security:
 *     permit-patterns:
 *       - "GET /sse/connect"   # 仅 GET 方法
 *       - "/public/open/**"    # 全方法通配
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "web-plus.security")
public class SecurityPermitProperties {

    /**
     * 额外放行路径列表。
     * 格式：{@code "METHOD /path/**"} 或 {@code "/path/**"}（不限方法）。
     */
    private List<String> permitPatterns = new ArrayList<>();

    public List<String> getPermitPatterns() {
        return permitPatterns;
    }

    public void setPermitPatterns(List<String> permitPatterns) {
        this.permitPatterns = permitPatterns != null ? permitPatterns : new ArrayList<>();
    }
}
