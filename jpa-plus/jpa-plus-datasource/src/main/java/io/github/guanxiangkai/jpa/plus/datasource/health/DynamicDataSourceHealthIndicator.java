package io.github.guanxiangkai.jpa.plus.datasource.health;

import io.github.guanxiangkai.jpa.plus.datasource.registry.DynamicDataSourceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 动态数据源健康检查指示器
 *
 * <p>检查所有已注册数据源的连通性，上报到 Spring Boot Actuator {@code /actuator/health}。</p>
 *
 * <h3>检查方式</h3>
 * <p>对每个数据源执行 {@link Connection#isValid(int)} 校验，
 * 超时或异常的数据源标记为 {@code DOWN}，全部通过则为 {@code UP}。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class DynamicDataSourceHealthIndicator implements HealthIndicator {

    /**
     * 连接校验超时（秒）
     */
    private static final int VALIDATION_TIMEOUT_SECONDS = 3;

    private final DynamicDataSourceRegistry registry;
    private final boolean includeDetail;

    /**
     * @param registry      数据源注册中心
     * @param includeDetail 是否包含各数据源详情
     */
    public DynamicDataSourceHealthIndicator(DynamicDataSourceRegistry registry, boolean includeDetail) {
        this.registry = registry;
        this.includeDetail = includeDetail;
    }

    @Override
    public Health health() {
        Set<String> names = registry.names();
        Map<String, Object> details = new LinkedHashMap<>();
        boolean allUp = true;

        for (String name : names) {
            DataSource ds = registry.get(name);
            if (ds == null) {
                details.put(name, "NOT_FOUND");
                allUp = false;
                continue;
            }

            try (Connection conn = ds.getConnection()) {
                if (conn.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                    details.put(name, "UP");
                } else {
                    details.put(name, "DOWN (validation failed)");
                    allUp = false;
                }
            } catch (Exception e) {
                details.put(name, "DOWN (" + e.getMessage() + ")");
                allUp = false;
                log.warn("Health check failed for datasource '{}': {}", name, e.getMessage());
            }
        }

        Health.Builder builder = allUp ? Health.up() : Health.down();
        if (includeDetail) {
            builder.withDetails(details);
        }
        return builder.withDetail("total", names.size()).build();
    }
}

