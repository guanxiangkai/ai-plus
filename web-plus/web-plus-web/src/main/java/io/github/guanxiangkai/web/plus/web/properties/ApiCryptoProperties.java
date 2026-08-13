package io.github.guanxiangkai.web.plus.web.properties;

import io.github.guanxiangkai.web.plus.web.crypto.ApiCryptoRuntimePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 接口入参与出参加密配置。
 *
 * <p>全局开关默认关闭。启用时必须由应用显式配置密钥；默认策略为
 * {@code SM4_CBC_SM3_V1}，默认密钥标识分别为 {@code request-default}
 * 和 {@code response-default}。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "web-plus.api-crypto")
public class ApiCryptoProperties {

    private static final ApiCryptoRuntimePolicy DEFAULT_RUNTIME_POLICY = ApiCryptoRuntimePolicy.defaults();

    /**
     * 接口加解密总开关，默认关闭。
     */
    private boolean enabled = false;

    /**
     * 默认加密策略。
     */
    private Strategy strategy = Strategy.SM4_CBC_SM3_V1;

    /**
     * AES-GCM 策略的 PBKDF2 迭代次数。
     */
    private int pbkdf2Iterations = 120_000;

    /**
     * 入参加密配置：前端加密，后端解密。
     */
    private EndpointCrypto request = new EndpointCrypto(true, "request-default");

    /**
     * 出参加密配置：后端加密，前端解密。
     */
    private EndpointCrypto response = new EndpointCrypto(true, "response-default");

    /**
     * 请求/响应聚合与加解密执行资源配置。
     */
    private RuntimeSettings runtime = new RuntimeSettings();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public int getPbkdf2Iterations() {
        return pbkdf2Iterations;
    }

    public void setPbkdf2Iterations(int pbkdf2Iterations) {
        this.pbkdf2Iterations = pbkdf2Iterations;
    }

    public EndpointCrypto getRequest() {
        return request;
    }

    public void setRequest(EndpointCrypto request) {
        this.request = request;
    }

    public EndpointCrypto getResponse() {
        return response;
    }

    public void setResponse(EndpointCrypto response) {
        this.response = response;
    }

    public RuntimeSettings getRuntime() {
        return runtime;
    }

    public void setRuntime(RuntimeSettings runtime) {
        this.runtime = runtime;
    }

    public boolean requestEnabled() {
        return enabled && request != null && request.isEnabled();
    }

    public boolean responseEnabled() {
        return enabled && response != null && response.isEnabled();
    }

    /**
     * 前后端约定的接口加密策略。
     */
    public enum Strategy {

        /**
         * AES-256-GCM，密钥由 PBKDF2WithHmacSHA256 派生。
         */
        AES_GCM_V1("AES-GCM-256"),

        /**
         * SM4-CBC-PKCS7 加密 + HMAC-SM3 完整性校验。
         */
        SM4_CBC_SM3_V1("SM4-CBC-SM3");

        private final String algorithm;

        Strategy(String algorithm) {
            this.algorithm = algorithm;
        }

        public String algorithm() {
            return algorithm;
        }

        public static Strategy fromAlgorithm(String algorithm) {
            for (Strategy value : values()) {
                if (value.algorithm.equalsIgnoreCase(algorithm)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("不支持的接口加密算法: " + algorithm);
        }
    }

    /**
     * 单向接口加密配置。
     */
    public static class EndpointCrypto {

        /**
         * 单向开关。全局开关开启后，可单独关闭请求解密或响应加密。
         */
        private boolean enabled;

        /**
         * 密钥标识，写入加密信封，便于密钥轮换。
         */
        private String keyId;

        /**
         * 对称密钥明文。全局开关关闭时可不配置；对应方向启用时必须配置。
         */
        private String key = "";

        public EndpointCrypto() {
        }

        EndpointCrypto(boolean enabled, String keyId) {
            this.enabled = enabled;
            this.keyId = keyId;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    /** API 加解密运行资源设置。 */
    public static class RuntimeSettings {

        private DataSize maxRequestBodySize = DEFAULT_RUNTIME_POLICY.maxRequestBodySize();
        private DataSize maxResponseBodySize = DEFAULT_RUNTIME_POLICY.maxResponseBodySize();
        private int maxQueryEnvelopeLength = DEFAULT_RUNTIME_POLICY.maxQueryEnvelopeLength();
        private int workerCount = DEFAULT_RUNTIME_POLICY.workerCount();
        private int taskQueueCapacity = DEFAULT_RUNTIME_POLICY.taskQueueCapacity();

        public DataSize getMaxRequestBodySize() {
            return maxRequestBodySize;
        }

        public void setMaxRequestBodySize(DataSize maxRequestBodySize) {
            this.maxRequestBodySize = maxRequestBodySize;
        }

        public DataSize getMaxResponseBodySize() {
            return maxResponseBodySize;
        }

        public void setMaxResponseBodySize(DataSize maxResponseBodySize) {
            this.maxResponseBodySize = maxResponseBodySize;
        }

        public int getMaxQueryEnvelopeLength() {
            return maxQueryEnvelopeLength;
        }

        public void setMaxQueryEnvelopeLength(int maxQueryEnvelopeLength) {
            this.maxQueryEnvelopeLength = maxQueryEnvelopeLength;
        }

        public int getWorkerCount() {
            return workerCount;
        }

        public void setWorkerCount(int workerCount) {
            this.workerCount = workerCount;
        }

        public int getTaskQueueCapacity() {
            return taskQueueCapacity;
        }

        public void setTaskQueueCapacity(int taskQueueCapacity) {
            this.taskQueueCapacity = taskQueueCapacity;
        }

        /** 转换为不可变且完成边界校验的运行策略。 */
        public ApiCryptoRuntimePolicy toPolicy() {
            return new ApiCryptoRuntimePolicy(
                    maxRequestBodySize,
                    maxResponseBodySize,
                    maxQueryEnvelopeLength,
                    workerCount,
                    taskQueueCapacity);
        }
    }
}
