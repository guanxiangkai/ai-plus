package io.github.guanxiangkai.web.plus.license.autoconfigure;

import io.github.guanxiangkai.web.plus.license.properties.LicenseProperties;
import io.github.guanxiangkai.web.plus.license.runtime.LicenseGuard;
import io.github.guanxiangkai.web.plus.license.runtime.LicenseRuntime;
import io.github.guanxiangkai.web.plus.license.web.LicenseWebFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 显式引入授权 starter 后强制校验；缺失或错误配置不会静默跳过。 */
@AutoConfiguration
@EnableConfigurationProperties(LicenseProperties.class)
public class LicenseAutoConfiguration {
    /** 完成首次授权后应用才可启动，并在关闭时停止续租。 */
    @Bean(initMethod = "start", destroyMethod = "close")
    public LicenseRuntime licenseRuntime(LicenseProperties properties) { return new LicenseRuntime(properties); }

    /** 供业务服务、任务及消息处理复用的授权关口。 */
    @Bean
    public LicenseGuard licenseGuard(LicenseRuntime runtime) { return runtime.guard(); }

    /** WebFlux 所有新请求均检查当前授权，不把鉴权路径自动加入白名单。 */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public LicenseWebFilter licenseWebFilter(LicenseGuard guard) { return new LicenseWebFilter(guard); }
}
