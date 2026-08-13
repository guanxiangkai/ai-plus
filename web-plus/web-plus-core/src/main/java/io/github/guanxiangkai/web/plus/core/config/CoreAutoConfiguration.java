package io.github.guanxiangkai.web.plus.core.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import java.util.TimeZone;

/**
 * Core 模块自动配置
 * <p>
 * Spring Boot 4 自动配置
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ComponentScan(basePackages = "io.github.guanxiangkai.web.plus.core")
public class CoreAutoConfiguration {

    @PostConstruct
    public void init() {
        // 设置系统默认时区为 Asia/Shanghai，避免使用已弃用的三字母时区ID
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        log.info("AI-Common-Core 模块已启用，系统时区设置为: {}", TimeZone.getDefault().getID());
    }
}