package io.github.guanxiangkai.web.plus.core.config;

import org.springframework.util.StringUtils;

import java.nio.file.Path;

/**
 * Nacos Java 客户端默认会在 user.home 下生成快照和日志，这里统一改到应用运行目录下。
 */
public final class NacosClientPathDefaults {

    private static final String RUNTIME_DIR_PROPERTY = "web-plus.runtime.dir";
    private static final String RUNTIME_DIR_ENV = "WEB_PLUS_RUNTIME_DIR";
    private static final String NACOS_SNAPSHOT_PATH_PROPERTY = "JM.SNAPSHOT.PATH";
    private static final String NACOS_LOG_PATH_PROPERTY = "JM.LOG.PATH";

    private NacosClientPathDefaults() {
    }

    public static void apply() {
        Path runtimeDir = resolveRuntimeDir();
        setDefaultSystemProperty(RUNTIME_DIR_PROPERTY, runtimeDir.toString());
        setDefaultSystemProperty(NACOS_SNAPSHOT_PATH_PROPERTY, runtimeDir.toString());
        setDefaultSystemProperty(NACOS_LOG_PATH_PROPERTY, runtimeDir.resolve("logs").toString());
    }

    private static Path resolveRuntimeDir() {
        String configuredRuntimeDir = System.getProperty(RUNTIME_DIR_PROPERTY);
        if (!StringUtils.hasText(configuredRuntimeDir)) {
            configuredRuntimeDir = System.getenv(RUNTIME_DIR_ENV);
        }
        if (StringUtils.hasText(configuredRuntimeDir)) {
            return Path.of(configuredRuntimeDir).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.dir"), ".runtime").toAbsolutePath().normalize();
    }

    private static void setDefaultSystemProperty(String key, String value) {
        if (!StringUtils.hasText(System.getProperty(key))) {
            System.setProperty(key, value);
        }
    }
}
