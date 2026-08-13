package io.github.guanxiangkai.jpa.plus.audit.interceptor;

import io.github.guanxiangkai.jpa.plus.audit.annotation.AuditExclude;
import io.github.guanxiangkai.jpa.plus.audit.snapshot.AuditSnapshot;
import io.github.guanxiangkai.jpa.plus.audit.snapshot.FieldDiff;
import io.github.guanxiangkai.jpa.plus.audit.snapshot.SnapshotSerializer;
import io.github.guanxiangkai.jpa.plus.core.util.ReflectionUtils;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Captures persisted and in-memory entity snapshots for audit diff generation.
 */
@Slf4j
final class AuditSnapshotCollector {

    private static final ClassValue<List<Field>> AUDIT_FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> result = new ArrayList<>();
            for (Field field : ReflectionUtils.getHierarchyFields(type)) {
                if (field.isAnnotationPresent(AuditExclude.class)) {
                    log.trace("[jpa-plus] snapshot: skipping @AuditExclude field '{}'", field.getName());
                    continue;
                }
                result.add(field);
            }
            return List.copyOf(result);
        }
    };

    private static final ClassValue<Optional<Field>> IDENTIFIER_FIELD = new ClassValue<>() {
        @Override
        protected Optional<Field> computeValue(Class<?> type) {
            return ReflectionUtils.getHierarchyFields(type).stream()
                    .filter(field -> field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(EmbeddedId.class))
                    .findFirst();
        }
    };

    private final EntityManager entityManager;
    private final SnapshotSerializer serializer;

    AuditSnapshotCollector(EntityManager entityManager, SnapshotSerializer serializer) {
        this.entityManager = entityManager;
        this.serializer = serializer != null ? serializer : SnapshotSerializer.NOOP;
    }

    /**
     * Loads the database-persisted snapshot of the entity before the operation.
     * Uses an isolated EntityManager so the current persistence context cannot pollute the before state.
     */
    Map<String, Object> loadPersistedSnapshot(Class<?> entityClass, Object entity) {
        Object entityId = extractIdentifier(entity);
        if (entityId == null) {
            return Map.of();
        }

        EntityManager snapshotEntityManager = createSnapshotEntityManager();
        if (snapshotEntityManager != null) {
            try (snapshotEntityManager) {
                return findPersistedSnapshot(snapshotEntityManager, entityClass, entityId);
            }
        }

        log.warn("[jpa-plus] SnapshotAuditInterceptor: unable to create isolated EntityManager for entity '{}'. " +
                "Audit snapshot will be empty (no before-values). Consider checking EMF lifecycle.", entityClass.getSimpleName());
        return Map.of();
    }

    AuditSnapshot diff(Class<?> entityClass,
                       Map<String, Object> before,
                       Map<String, Object> after) {
        Map<String, FieldDiff> diffs = new LinkedHashMap<>();
        for (String field : after.keySet()) {
            Object beforeVal = before.get(field);
            Object afterVal = after.get(field);
            if (!java.util.Objects.equals(beforeVal, afterVal)) {
                diffs.put(field, new FieldDiff(field, beforeVal, afterVal));
            }
        }
        return new AuditSnapshot(entityClass, diffs);
    }

    AuditSnapshot deleteSnapshot(Class<?> entityClass, Map<String, Object> beforeValues) {
        Map<String, FieldDiff> diffs = new LinkedHashMap<>();
        beforeValues.forEach((field, value) -> diffs.put(field, new FieldDiff(field, value, null)));
        return new AuditSnapshot(entityClass, diffs);
    }

    Map<String, Object> captureValues(Object entity, Class<?> entityClass) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field field : AUDIT_FIELDS.get(entityClass)) {
            try {
                Object raw = field.get(entity);
                values.put(field.getName(), serializer.serializeValue(field.getName(), raw));
            } catch (Exception e) {
                log.trace("[jpa-plus] snapshot: cannot read field '{}': {}", field.getName(), e.getMessage());
            }
        }
        return values;
    }

    private Object extractIdentifier(Object entity) {
        Object identifier = extractIdentifierViaPersistenceUnit(entity);
        return identifier != null ? identifier : extractIdentifierViaField(entity);
    }

    private Object extractIdentifierViaPersistenceUnit(Object entity) {
        try {
            var entityManagerFactory = entityManager.getEntityManagerFactory();
            if (entityManagerFactory == null) return null;
            var persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
            return persistenceUnitUtil != null ? persistenceUnitUtil.getIdentifier(entity) : null;
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.trace("[jpa-plus] snapshot: cannot resolve identifier via PersistenceUnitUtil for {}: {}",
                    entity.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private Object extractIdentifierViaField(Object entity) {
        Optional<Field> idField = IDENTIFIER_FIELD.get(entity.getClass());
        if (idField.isEmpty()) {
            return null;
        }
        try {
            return idField.get().get(entity);
        } catch (Exception e) {
            return null;
        }
    }

    private EntityManager createSnapshotEntityManager() {
        try {
            var entityManagerFactory = entityManager.getEntityManagerFactory();
            return entityManagerFactory != null ? entityManagerFactory.createEntityManager() : null;
        } catch (IllegalStateException e) {
            log.trace("[jpa-plus] snapshot: cannot create isolated EntityManager: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> findPersistedSnapshot(EntityManager source, Class<?> entityClass, Object entityId) {
        Object persisted = source.find(entityClass, entityId);
        return persisted != null ? captureValues(persisted, entityClass) : Map.of();
    }
}
