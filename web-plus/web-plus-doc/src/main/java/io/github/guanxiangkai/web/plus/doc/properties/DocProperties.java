package io.github.guanxiangkai.web.plus.doc.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * web-plus-doc 配置属性
 *
 * <h3>API 分组配置示例</h3>
 * <pre>{@code
 * web-plus:
 *   doc:
 *     title: My API
 *     groups:
 *       - name: 用户模块
 *         paths-to-match: /user/**, /auth/**
 *         description: 用户相关接口
 *       - name: 订单模块
 *         paths-to-match: /order/**
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "web-plus.doc")
public record DocProperties(
        Boolean enabled,
        String title,
        String version,
        String description,
        ContactInfo contact,
        ServerInfo server,
        List<GroupConfig> groups
) {
    public DocProperties {
        if (enabled == null) enabled = true;
        if (title == null) title = "Web Plus API";
        if (version == null) version = "2026.2.0";
        if (description == null) description = "企业级 Web 通用能力接口文档";
        if (contact == null) contact = new ContactInfo(null, null, null);
        if (server == null) server = new ServerInfo(null, null);
        if (groups == null) groups = List.of();
    }

    public record ContactInfo(String name, String email, String url) {
        public ContactInfo {
            if (name == null) name = "AI Plus";
            if (email == null) email = "";
            if (url == null) url = "https://github.com/guanxiangkai/ai-plus";
        }
    }

    public record ServerInfo(String url, String description) {
        public ServerInfo {
            if (url == null) url = "http://localhost:8080";
            if (description == null) description = "开发环境";
        }
    }

    /**
     * API 分组配置
     *
     * @param name         分组名称（唯一，Swagger UI 下拉框中显示）
     * @param pathsToMatch 该分组匹配的路径模式列表（支持 Ant 风格通配符，如 {@code /user/**}）
     * @param description  分组描述（可选）
     */
    public record GroupConfig(
            String name,
            List<String> pathsToMatch,
            String description
    ) {
        public GroupConfig {
            if (pathsToMatch == null) pathsToMatch = List.of("/**");
            if (description == null) description = "";
        }
    }
}
