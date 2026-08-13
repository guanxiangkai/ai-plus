package io.github.guanxiangkai.web.plus.web.util;

import io.github.guanxiangkai.jpa.plus.query.wrapper.TypedGetter;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * JPA Criteria Specification 类型安全构造工具
 *
 * <p>通过 {@link TypedGetter}（方法引用）代替字符串字段名，在编译期检测字段拼写错误，
 * 防止 {@code root.get("fieldName")} 字符串引用在重构时静默失效。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * protected Specification<User> buildQuerySpec(UserPageDTO dto) {
 *     return SpecUtils.<User>builder()
 *             .likeIfPresent(User::getUsername, dto.getUsername())
 *             .eqIfPresent(User::getUserStatus, dto.getUserStatus())
 *             .geTimeIfPresent(User::getCreateTime, dto.getStartTime())
 *             .leTimeIfPresent(User::getCreateTime, dto.getEndTime())
 *             .build();
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class SpecUtils {

    private SpecUtils() {
    }

    public static <E> Builder<E> builder() {
        return new Builder<>();
    }

    // ─────────────────────── Static factory shortcuts ───────────────────────

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public static <E, V> Specification<E> eq(TypedGetter<E, V> getter, V value) {
        if (value == null) return null;
        String field = fieldName(getter);
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    public static <E> Specification<E> like(TypedGetter<E, String> getter, String value) {
        if (!StringUtils.hasText(value)) return null;
        String field = fieldName(getter);
        String escaped = escapeLike(value);
        return (root, query, cb) -> cb.like(root.get(field), "%" + escaped + "%", '\\');
    }

    public static <E> Specification<E> isNull(TypedGetter<E, ?> getter) {
        String field = fieldName(getter);
        return (root, query, cb) -> cb.isNull(root.get(field));
    }

    public static <E, V extends Comparable<V>> Specification<E> ge(TypedGetter<E, V> getter, V value) {
        if (value == null) return null;
        String field = fieldName(getter);
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), value);
    }

    public static <E, V extends Comparable<V>> Specification<E> le(TypedGetter<E, V> getter, V value) {
        if (value == null) return null;
        String field = fieldName(getter);
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get(field), value);
    }

    public static <E, V> Specification<E> in(TypedGetter<E, V> getter, Collection<V> values) {
        if (values == null || values.isEmpty()) return null;
        String field = fieldName(getter);
        return (root, query, cb) -> root.get(field).in(values);
    }

    // ────────────────────────── Time range helpers ──────────────────────────

    public static <E> Specification<E> geTime(TypedGetter<E, LocalDateTime> getter, String isoTime) {
        if (!StringUtils.hasText(isoTime)) return null;
        LocalDateTime time = LocalDateTime.parse(isoTime);
        String field = fieldName(getter);
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.<LocalDateTime>get(field), time);
    }

    public static <E> Specification<E> leTime(TypedGetter<E, LocalDateTime> getter, String isoTime) {
        if (!StringUtils.hasText(isoTime)) return null;
        LocalDateTime time = LocalDateTime.parse(isoTime);
        String field = fieldName(getter);
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.<LocalDateTime>get(field), time);
    }

    // ────────────────────────── Internal ──────────────────────────

    /**
     * 从 Lambda 方法引用中提取 JPA 属性名（Java camelCase 字段名）。
     *
     * <p>JPA Criteria {@code root.get()} 需要 Java 属性名（camelCase），
     * 此处借助 {@link java.lang.invoke.SerializedLambda} 序列化机制提取 getter 方法名
     * 并还原为 Java 字段名，避免字符串字面量在重构时静默失效。</p>
     */
    static <E, V> String fieldName(TypedGetter<E, V> getter) {
        try {
            java.lang.reflect.Method writeReplace = getter.getClass().getDeclaredMethod("writeReplace");
            java.lang.invoke.MethodHandles.Lookup lookup =
                    java.lang.invoke.MethodHandles.privateLookupIn(getter.getClass(), java.lang.invoke.MethodHandles.lookup());
            java.lang.invoke.SerializedLambda lambda =
                    (java.lang.invoke.SerializedLambda) lookup.unreflect(writeReplace).bindTo(getter).invokeWithArguments();
            String methodName = lambda.getImplMethodName();
            String raw;
            if (methodName.startsWith("get") && methodName.length() > 3) {
                raw = methodName.substring(3);
            } else if (methodName.startsWith("is") && methodName.length() > 2) {
                raw = methodName.substring(2);
            } else {
                raw = methodName;
            }
            return Character.toLowerCase(raw.charAt(0)) + raw.substring(1);
        } catch (Throwable t) {
            throw new IllegalStateException("Cannot extract field name from getter reference", t);
        }
    }

    // ────────────────────────── Fluent Builder ──────────────────────────

    public static final class Builder<E> {

        private final List<Specification<E>> specs = new ArrayList<>();

        public Builder<E> eqIfPresent(TypedGetter<E, ?> getter, Object value) {
            if (value != null && !(value instanceof String s && !StringUtils.hasText(s))) {
                String field = fieldName(getter);
                specs.add((root, query, cb) -> cb.equal(root.get(field), value));
            }
            return this;
        }

        public Builder<E> likeIfPresent(TypedGetter<E, String> getter, String value) {
            if (StringUtils.hasText(value)) {
                String field = fieldName(getter);
                String escaped = escapeLike(value);
                specs.add((root, query, cb) -> cb.like(root.get(field), "%" + escaped + "%", '\\'));
            }
            return this;
        }

        public <V extends Comparable<V>> Builder<E> geIfPresent(TypedGetter<E, V> getter, V value) {
            if (value != null) {
                String field = fieldName(getter);
                specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), value));
            }
            return this;
        }

        public <V extends Comparable<V>> Builder<E> leIfPresent(TypedGetter<E, V> getter, V value) {
            if (value != null) {
                String field = fieldName(getter);
                specs.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get(field), value));
            }
            return this;
        }

        public Builder<E> geTimeIfPresent(TypedGetter<E, LocalDateTime> getter, String isoTime) {
            if (StringUtils.hasText(isoTime)) {
                String field = fieldName(getter);
                LocalDateTime time = LocalDateTime.parse(isoTime);
                specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), time));
            }
            return this;
        }

        public Builder<E> leTimeIfPresent(TypedGetter<E, LocalDateTime> getter, String isoTime) {
            if (StringUtils.hasText(isoTime)) {
                String field = fieldName(getter);
                LocalDateTime time = LocalDateTime.parse(isoTime);
                specs.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get(field), time));
            }
            return this;
        }

        public <V> Builder<E> inIfPresent(TypedGetter<E, V> getter, Collection<V> values) {
            if (values != null && !values.isEmpty()) {
                String field = fieldName(getter);
                specs.add((root, query, cb) -> root.get(field).in(values));
            }
            return this;
        }

        /** 追加自定义 Specification（处理无法用通用方法覆盖的复杂条件）。 */
        public Builder<E> andIf(boolean condition, Supplier<Specification<E>> specSupplier) {
            if (condition) {
                specs.add(specSupplier.get());
            }
            return this;
        }

        public Specification<E> build() {
            if (specs.isEmpty()) return (root, query, cb) -> cb.conjunction();
            return specs.stream()
                    .reduce(Specification::and)
                    .orElseGet(() -> (root, query, cb) -> cb.conjunction());
        }
    }
}
