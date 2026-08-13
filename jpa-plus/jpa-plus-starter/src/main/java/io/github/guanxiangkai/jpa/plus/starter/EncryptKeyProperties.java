package io.github.guanxiangkai.jpa.plus.starter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 字段加密密钥配置。
 *
 * <pre>{@code
 * jpa-plus:
 *   encrypt:
 *     enabled: true
 *     active-version: primary
 *     keys:
 *       primary: ${JPA_PLUS_ENCRYPT_PRIMARY_KEY}
 * }</pre>
 *
 * <p><b>安全要求：</b>密钥长度不得少于 16 个字符，请勿使用默认值或简单密钥。
 * 密钥应通过环境变量或密钥管理服务注入，避免硬编码在配置文件中。</p>
 *
 * @since 1.0.0
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "jpa-plus.encrypt")
public class EncryptKeyProperties {

    /**
     * 当前写入密钥版本。
     */
    @NotBlank(message = "jpa-plus.encrypt.active-version 不能为空")
    private String activeVersion = "primary";

    /**
     * 版本与密钥的只读映射。
     */
    @NotEmpty(message = "jpa-plus.encrypt.keys 不能为空，必须配置密钥")
    private Map<@NotBlank String, @NotBlank @Size(min = 16) String> keys = new LinkedHashMap<>();

    /**
     * 返回不可变密钥视图，防止外部修改。
     */
    public Map<String, String> getKeys() {
        return Collections.unmodifiableMap(keys);
    }

    /**
     * 接收 Spring 配置绑定结果并校验每一项。
     */
    public void setKeys(Map<String, String> keys) {
        if (keys != null) {
            for (Map.Entry<String, String> entry : keys.entrySet()) {
                String v = entry.getValue();
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw new IllegalArgumentException("jpa-plus.encrypt.keys 不能包含空版本号");
                }
                if (v == null || v.length() < 16) {
                    throw new IllegalArgumentException(
                            "jpa-plus.encrypt.keys[" + entry.getKey() + "] 长度不能少于 16 个字符");
                }
            }
        }
        this.keys = keys == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keys);
    }
}
