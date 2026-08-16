package io.github.guanxiangkai.web.plus.log.bridge;

import io.github.guanxiangkai.jpa.plus.audit.event.DataAuditEvent;
import io.github.guanxiangkai.jpa.plus.audit.snapshot.AuditSnapshot;
import io.github.guanxiangkai.jpa.plus.audit.snapshot.FieldDiff;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;
import io.github.guanxiangkai.web.plus.core.entity.BaseEntity;
import io.github.guanxiangkai.web.plus.log.context.OperationLogContext;
import io.github.guanxiangkai.web.plus.log.context.OperationLogContext.OperationContext;
import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import io.github.guanxiangkai.web.plus.log.model.FieldChange;
import io.github.guanxiangkai.web.plus.log.spi.DataChangeHandler;
import io.github.guanxiangkai.web.plus.log.support.LogEntityBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * jpa-plus 数据审计桥接器
 *
 * <p>
 * 监听 jpa-plus {@link DataAuditEvent}，通过 {@link LogEntityBinder} 创建
 * 用户配置的数据变更日志实体，填充字段后交给 {@link DataChangeHandler} 持久化。
 * </p>
 *
 * <p>实体类通过 {@code web-plus.log.data-change-entity-class} 配置，
 * 字段变更列表以 JSON 字符串形式写入实体的 {@code fieldChanges} 字段。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class JpaPlusDataAuditEventBridge {

    private final DataChangeHandler dataChangeHandler;
    private final ObjectMapper objectMapper;
    /**
     * 用户配置的数据变更日志实体类（需继承 BaseLog）；为 null 时跳过实体创建
     */
    private final Class<?> entityClass;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDataAudit(DataAuditEvent event) {
        if (entityClass == null || entityClass == Void.class || dataChangeHandler == null) {
            return;
        }
        OperationContext ctx = OperationLogContext.current();
        if (ctx == null) return;

        try {
            String entityType = resolveEntityType(event);
            String entityId = resolveEntityId(event.entity());
            String changeType = resolveChangeType(event, ctx);
            List<FieldChange> changes = mapFieldChanges(event.snapshot());
            String fieldChangesJson = toJson(changes);
            LocalDateTime changeTime = LocalDateTime.ofInstant(event.timestamp(), ZoneId.systemDefault());

            log.debug("[data-change] opId={} entity={}#{} type={} fields={}",
                    ctx.operationId(), entityType, entityId, changeType, changes.size());

            BaseLog entity = LogEntityBinder.newInstance(entityClass);
            if (entity == null) return;

            LogEntityBinder.bindCommon(entity, ctx.traceId(), ctx.userId(), null, ctx.tenantId(),
                    null, "SUCCESS", null);
            LogEntityBinder.set(entity, "operationId", ctx.operationId());
            LogEntityBinder.set(entity, "entityType", entityType);
            LogEntityBinder.set(entity, "entityId", entityId);
            LogEntityBinder.set(entity, "changeType", changeType);
            LogEntityBinder.set(entity, "fieldChanges", fieldChangesJson);
            LogEntityBinder.set(entity, "changeTime", changeTime);

            dataChangeHandler.handle(entity);
        } catch (Exception e) {
            log.error("[web-plus] jpa-plus DataAuditEvent 桥接失败: exception={}",
                    e.getClass().getSimpleName());
        }
    }

    private String resolveEntityType(DataAuditEvent event) {
        AuditSnapshot snapshot = event.snapshot();
        if (snapshot != null && snapshot.entityClass() != null) {
            return snapshot.entityClass().getSimpleName();
        }
        Object entity = event.entity();
        return entity != null ? entity.getClass().getSimpleName() : null;
    }

    private List<FieldChange> mapFieldChanges(AuditSnapshot snapshot) {
        if (snapshot == null || snapshot.diffs() == null || snapshot.diffs().isEmpty()) {
            return List.of();
        }
        return snapshot.diffs().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .map(diff -> new FieldChange(diff.fieldName(), diff.before(), diff.after()))
                .toList();
    }

    private String resolveChangeType(DataAuditEvent event, OperationContext ctx) {
        if (event.operation() == OperationType.DELETE) return "DELETE";

        String typeCode = ctx.operationTypeCode();
        if (isInsertOperation(typeCode)) return "INSERT";
        if (isUpdateOperation(typeCode)) return "UPDATE";

        AuditSnapshot snapshot = event.snapshot();
        if (snapshot != null && snapshot.hasChanges() && isAllAdded(snapshot.diffs())) return "INSERT";

        return "UPDATE";
    }

    private boolean isInsertOperation(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) return false;
        String n = typeCode.strip().toUpperCase(Locale.ROOT);
        return "INSERT".equals(n) || "CREATE".equals(n) || "ADD".equals(n);
    }

    private boolean isUpdateOperation(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) return false;
        String n = typeCode.strip().toUpperCase(Locale.ROOT);
        return "UPDATE".equals(n) || "EDIT".equals(n) || "MODIFY".equals(n);
    }

    private boolean isAllAdded(Map<String, FieldDiff> diffs) {
        return diffs != null && !diffs.isEmpty()
                && diffs.values().stream().filter(Objects::nonNull)
                .allMatch(diff -> diff.before() == null && diff.after() != null);
    }

    private String resolveEntityId(Object entity) {
        if (entity == null) return null;
        if (entity instanceof BaseEntity baseEntity) return baseEntity.getId();
        Object value = invokeIdGetter(entity);
        if (value != null) return String.valueOf(value);
        value = readIdField(entity);
        return value != null ? String.valueOf(value) : null;
    }

    private Object invokeIdGetter(Object entity) {
        try {
            Method method = entity.getClass().getMethod("getId");
            return method.invoke(entity);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object readIdField(Object entity) {
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if ("id".equals(field.getName())) {
                    try {
                        field.setAccessible(true);
                        return field.get(entity);
                    } catch (IllegalAccessException ignored) {
                        return null;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
