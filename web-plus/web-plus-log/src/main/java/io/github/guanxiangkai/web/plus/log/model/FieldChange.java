package io.github.guanxiangkai.web.plus.log.model;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

/**
 * 数据变更字段快照（单个字段的变更记录）
 *
 * <p>
 * 由 {@link io.github.guanxiangkai.web.plus.log.bridge.JpaPlusDataAuditEventBridge}
 * 从 jpa-plus {@code AuditSnapshot.diffs()} 转换而来，
 * 最终以 JSON 字符串形式序列化后写入日志实体的 {@code fieldChanges} 字段。
 * </p>
 *
 * @param fieldName 字段名称（Java 属性名）
 * @param before    变更前的值（{@code null} 表示新增场景）
 * @param after     变更后的值（{@code null} 表示删除场景）
 * @author guanxiangkai
 * @since 1.0.0
 */
@RegisterReflectionForBinding
public record FieldChange(
        String fieldName,
        Object before,
        Object after
) {
}

