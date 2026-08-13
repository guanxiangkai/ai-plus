package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.field.encrypt.spi.EncryptKeyProvider;
import org.springframework.beans.factory.DisposableBean;

import java.util.*;

/**
 * 基于配置属性的密钥提供者。
 *
 * <h3>内存安全</h3>
 * <p>密钥在内部以 {@code char[]} 存储（而非 {@code String}），
 * 并在 Spring 容器关闭时通过 {@link DisposableBean#destroy()} 用零字节覆盖，
 * 以减少密钥在堆内存中的暴露窗口，降低堆转储泄露风险。</p>
 *
 * <p><b>注意：</b>{@link #getKeyByVersion(String)} 仍返回 {@code String}
 * （受 {@link EncryptKeyProvider} 接口约束），调用时会在堆上短暂创建明文 String。
 * 若对密钥生命周期有严格要求，请实现自定义 {@link EncryptKeyProvider} 并配合 HSM 或
 * Java {@code KeyStore} 使用。</p>
 *
 * @since 1.0.0
 */
public class PropertiesEncryptKeyProvider implements EncryptKeyProvider, DisposableBean {

    private static final String DEFAULT_VERSION = "primary";

    private final String activeVersion;
    /** 使用可擦除字符数组保存密钥。 */
    private final Map<String, char[]> versionedKeys;

    public PropertiesEncryptKeyProvider(EncryptKeyProperties properties) {
        String configuredVersion = normalizeVersion(properties.getActiveVersion());
        LinkedHashMap<String, char[]> keys = new LinkedHashMap<>();
        properties.getKeys().forEach((version, key) -> keys.put(normalizeVersion(version), key.toCharArray()));

        if (!keys.containsKey(configuredVersion)) {
            throw new IllegalStateException(
                    "[jpa-plus] 未配置当前加密密钥版本 '" + configuredVersion
                            + "'，请设置 jpa-plus.encrypt.keys." + configuredVersion
                            + " 或调整 active-version；已配置版本：" + keys.keySet());
        }

        this.activeVersion = configuredVersion;
        this.versionedKeys = Map.copyOf(keys);
    }

    private static String normalizeVersion(String version) {
        if (version == null || version.isBlank()) {
            return DEFAULT_VERSION;
        }
        return version.trim();
    }

    @Override
    public String getActiveVersion() {
        return activeVersion;
    }

    @Override
    public String getKeyByVersion(String version) {
        char[] keyChars = versionedKeys.get(normalizeVersion(version));
        if (keyChars == null) {
            throw new IllegalArgumentException("未配置加密密钥版本: " + version + "，已配置版本: " + versionedKeys.keySet());
        }
        return new String(keyChars);
    }

    /** Spring 容器关闭时擦除全部密钥字符。 */
    @Override
    public void destroy() {
        versionedKeys.values().forEach(keyChars -> Arrays.fill(keyChars, '\0'));
    }
}
