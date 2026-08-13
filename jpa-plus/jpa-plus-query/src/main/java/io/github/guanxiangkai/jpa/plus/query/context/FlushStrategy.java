package io.github.guanxiangkai.jpa.plus.query.context;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;

/**
 * Flush 策略
 *
 * <p>根据配置的 {@link FlushMode} 决定是否在查询前执行 flush。
 * 使用 Hibernate {@code Session.isDirty()} 替代反射检测脏数据，
 * 保证类型安全与可维护性。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@RequiredArgsConstructor
public class FlushStrategy {

    private final FlushMode mode;

    /**
     * 根据策略决定是否需要 flush
     */
    public void flushIfNeeded(EntityManager entityManager) {
        switch (mode) {
            case ALWAYS -> entityManager.flush();
            case AUTO -> {
                if (hasPendingChanges(entityManager)) {
                    entityManager.flush();
                }
            }
            case NEVER -> { /* 什么都不做 */ }
        }
    }

    private boolean hasPendingChanges(EntityManager entityManager) {
        try {
            Session session = entityManager.unwrap(Session.class);
            return session.isDirty();
        } catch (Exception e) {
            // 无法检测时，保守地执行 flush
            return true;
        }
    }
}

