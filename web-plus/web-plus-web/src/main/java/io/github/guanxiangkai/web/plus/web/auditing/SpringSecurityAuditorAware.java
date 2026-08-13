package io.github.guanxiangkai.web.plus.web.auditing;

import io.github.guanxiangkai.web.plus.security.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * Spring Security 审计信息提供者（适配器模式）
 * <p>
 * 驱动 {@code @CreatedBy} / {@code @LastModifiedBy} 字段自动填充。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class SpringSecurityAuditorAware implements AuditorAware<String> {

    @Override
    @NonNull
    public Optional<String> getCurrentAuditor() {
        try {
            return Optional.ofNullable(SecurityUtils.getUserId());
        } catch (Exception e) {
            log.debug("获取审计用户 ID 失败（可能为非登录上下文）: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
