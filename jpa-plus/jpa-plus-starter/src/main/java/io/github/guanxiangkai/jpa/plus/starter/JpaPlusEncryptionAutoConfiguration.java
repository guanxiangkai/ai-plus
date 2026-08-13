package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.field.encrypt.handler.EncryptFieldHandler;
import io.github.guanxiangkai.jpa.plus.field.encrypt.spi.EncryptKeyProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * JPA Plus 字段加密自动装配。
 *
 * <p>只有显式设置 {@code jpa-plus.encrypt.enabled=true} 时才绑定并校验密钥，
 * 未启用字段加密的应用无需配置任何密钥。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration(before = JpaPlusFieldAutoConfiguration.class)
@ConditionalOnProperty(prefix = "jpa-plus.encrypt", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EncryptKeyProperties.class)
public class JpaPlusEncryptionAutoConfiguration {

    /** 根据配置创建密钥提供者。 */
    @Bean
    @ConditionalOnMissingBean
    EncryptKeyProvider encryptKeyProvider(EncryptKeyProperties properties) {
        return new PropertiesEncryptKeyProvider(properties);
    }

    /** 创建字段加密处理器。 */
    @Bean
    @ConditionalOnMissingBean
    EncryptFieldHandler encryptFieldHandler(EncryptKeyProvider keyProvider) {
        return new EncryptFieldHandler(keyProvider);
    }
}
