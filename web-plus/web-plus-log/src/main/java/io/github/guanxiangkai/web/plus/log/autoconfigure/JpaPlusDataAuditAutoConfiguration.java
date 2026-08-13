package io.github.guanxiangkai.web.plus.log.autoconfigure;

import io.github.guanxiangkai.jpa.plus.audit.event.DataAuditEvent;
import io.github.guanxiangkai.web.plus.log.bridge.JpaPlusDataAuditEventBridge;
import io.github.guanxiangkai.web.plus.log.properties.LogProperties;
import io.github.guanxiangkai.web.plus.log.spi.DataChangeHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * jpa-plus 数据审计桥接自动装配
 *
 * <p>
 * 当应用 classpath 中存在 jpa-plus 审计模块时，自动注册
 * {@link JpaPlusDataAuditEventBridge}，将 {@link DataAuditEvent}
 * 直接桥接为 web-plus-log 的 {@link DataChangeHandler} SPI。
 * </p>
 *
 * <p>实体类通过 {@code web-plus.log.data-change-entity-class} 配置。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration(after = WebPlusLogAutoConfiguration.class)
@ConditionalOnClass(DataAuditEvent.class)
@ConditionalOnBean(DataChangeHandler.class)
@ConditionalOnProperty(prefix = "web-plus.log", name = {"enabled", "data-audit-enabled"},
        havingValue = "true", matchIfMissing = true)
public class JpaPlusDataAuditAutoConfiguration {

    private static Class<?> resolveClass(String className) {
        if (className == null || className.isBlank()) return null;
        try {
            return Class.forName(className.strip());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public JpaPlusDataAuditEventBridge jpaPlusDataAuditEventBridge(
            DataChangeHandler dataChangeHandler,
            ObjectMapper objectMapper,
            LogProperties props) {
        Class<?> entityClass = resolveClass(props.dataChangeEntityClass());
        return new JpaPlusDataAuditEventBridge(dataChangeHandler, objectMapper, entityClass);
    }
}
