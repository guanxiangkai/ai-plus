package io.github.guanxiangkai.web.plus.license.properties;

import io.github.guanxiangkai.web.plus.license.LicenseMode;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 项目授权配置；引入 starter 即要求授权，不提供运行时关闭校验的开关。
 *
 * @param mode OFFLINE 签名许可证或 ONLINE 短期租约，必须明确指定
 * @param issuer 预置的可信发行者标识
 * @param subject 被授权客户标识
 * @param product 当前业务产品标识
 * @param instance 当前安装实例标识；不是不可伪造的硬件证明
 * @param publicKey 由部署方独立管理的可信公钥文件
 * @param licenseFile 离线许可证文件
 * @param leaseEndpoint 在线租约 HTTPS 地址
 * @param credentialFile 在线续租凭据文件，内容不得写入应用配置
 * @param refreshInterval 在线续租间隔，默认 30 秒，范围 1 秒至 5 分钟
 * @param requestTimeout 在线请求总超时，默认 5 秒，范围 1 至 30 秒
 * @param requiredFeatures 应用整体运行必须具备的授权功能
 */
@ConfigurationProperties("web-plus.license")
public record LicenseProperties(LicenseMode mode, String issuer, String subject, String product,
        String instance, Path publicKey, Path licenseFile, URI leaseEndpoint, Path credentialFile,
        Duration refreshInterval, Duration requestTimeout, Set<String> requiredFeatures) {
    /** 只归一化默认值；启动时统一校验全部模式相关约束。 */
    public LicenseProperties {
        if (refreshInterval == null) refreshInterval = Duration.ofSeconds(30);
        if (requestTimeout == null) requestTimeout = Duration.ofSeconds(5);
        requiredFeatures = requiredFeatures == null ? Set.of() : Set.copyOf(requiredFeatures);
    }

    /** 拒绝未指定模式、缺少信任材料或不安全的在线地址。 */
    public void validate() {
        if (mode == null || blank(issuer) || blank(subject) || blank(product) || blank(instance) || publicKey == null) {
            throw new IllegalArgumentException("授权模式、发行者、客户、产品、实例及可信公钥必须配置");
        }
        if (refreshInterval.compareTo(Duration.ofSeconds(1)) < 0
                || refreshInterval.compareTo(Duration.ofMinutes(5)) > 0
                || requestTimeout.compareTo(Duration.ofSeconds(1)) < 0
                || requestTimeout.compareTo(Duration.ofSeconds(30)) > 0
                || requestTimeout.compareTo(refreshInterval) >= 0) {
            throw new IllegalArgumentException("授权续租间隔或请求超时超出允许范围，且超时必须小于续租间隔");
        }
        if (requiredFeatures.stream().anyMatch(LicenseProperties::blank)) {
            throw new IllegalArgumentException("必需授权功能不能为空");
        }
        if (mode == LicenseMode.OFFLINE && (licenseFile == null || leaseEndpoint != null || credentialFile != null)) {
            throw new IllegalArgumentException("离线模式仅配置许可证文件，不接受在线地址或凭据");
        }
        if (mode == LicenseMode.ONLINE && (licenseFile != null || credentialFile == null || leaseEndpoint == null
                || !"https".equalsIgnoreCase(leaseEndpoint.getScheme()) || leaseEndpoint.getHost() == null
                || leaseEndpoint.getUserInfo() != null || leaseEndpoint.getFragment() != null
                || leaseEndpoint.getQuery() != null)) {
            throw new IllegalArgumentException("在线模式必须配置无用户信息、查询或片段的 HTTPS 租约地址和凭据文件");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
