package io.github.guanxiangkai.jpa.plus.field.encrypt.spi;

/**
 * 加密密钥提供者（SPI）
 *
 * <p>用户实现此接口提供加密密钥，解耦密钥管理与加解密逻辑。
 * 密钥来源完全由用户控制 —— 可以从配置文件、环境变量、KMS、Vault 等获取。</p>
 *
 * <p>所有密文都带密钥版本前缀，解密时按密文版本精确获取密钥。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface EncryptKeyProvider {

    /**
     * 当前生效密钥版本（用于新数据加密）
     */
    String getActiveVersion();

    /**
     * 按版本获取密钥
     *
     * @param version 密钥版本（如 v1 / v2）
     * @return 对应版本的密钥
     */
    String getKeyByVersion(String version);
}
