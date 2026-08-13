package io.github.guanxiangkai.jpa.plus.audit.snapshot;

import java.util.Map;

/**
 * 实体快照（轻量审计）
 *
 * <p>记录一次 SAVE 操作前后的字段变更差异，可附加到 {@link io.github.guanxiangkai.jpa.plus.audit.event.DataAuditEvent}。
 * 轻量版本仅做字段级对比，不依赖 JaVers 等重型审计框架。</p>
 *
 * <h3>典型使用场景</h3>
 * <ul>
 *   <li>记录实体哪些字段被修改以及修改前后的值</li>
 *   <li>满足审计合规要求的字段变更日志</li>
 *   <li>可在事务提交后异步持久化审计日志</li>
 * </ul>
 *
 * <p>包含被审计的实体类型以及字段变更详情映射（字段名 → {@link FieldDiff}）。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public record AuditSnapshot(
        Class<?> entityClass,
        Map<String, FieldDiff> diffs
) {

    public AuditSnapshot {
        diffs = diffs == null ? Map.of() : Map.copyOf(diffs);
    }

    /**
     * 是否有任何字段发生变更
     */
    public boolean hasChanges() {
        return !diffs.isEmpty();
    }

    /**
     * 是否指定字段发生了变更
     */
    public boolean hasChanged(String fieldName) {
        return diffs.containsKey(fieldName);
    }
}

