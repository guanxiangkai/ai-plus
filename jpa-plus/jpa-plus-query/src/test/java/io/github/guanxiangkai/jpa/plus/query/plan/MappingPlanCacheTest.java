package io.github.guanxiangkai.jpa.plus.query.plan;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;
import io.github.guanxiangkai.jpa.plus.query.metadata.TableMeta;
import io.github.guanxiangkai.jpa.plus.query.wrapper.SelectColumn;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MappingPlanCacheTest {

    @Test
    void compile_isolatesPlansForSameNamedTypesFromDifferentClassLoaders() throws Exception {
        Class<?> firstType = loadIsolatedRow();
        Class<?> secondType = loadIsolatedRow();

        MappingPlan<?> firstPlan = MappingPlanCompiler.compile(firstType, List.of());
        MappingPlan<?> secondPlan = MappingPlanCompiler.compile(secondType, List.of());

        assertThat(firstPlan).isNotSameAs(secondPlan);
        assertThat(firstPlan.apply(mock(ResultSet.class))).isInstanceOf(firstType);
        assertThat(secondPlan.apply(mock(ResultSet.class))).isInstanceOf(secondType);
    }

    @Test
    void compile_keepsColumnLabelBoundariesInTheCacheKey() throws Exception {
        MappingPlan<CacheRow> first = MappingPlanCompiler.compile(CacheRow.class,
                columns("a,b", "c"));
        MappingPlan<CacheRow> second = MappingPlanCompiler.compile(CacheRow.class,
                columns("a", "b,c"));
        ResultSet firstResultSet = mock(ResultSet.class);
        when(firstResultSet.getObject(2, String.class)).thenReturn("first c");
        ResultSet secondResultSet = mock(ResultSet.class);
        when(secondResultSet.getObject(1, String.class)).thenReturn("second a");

        assertThat(second).isNotSameAs(first);
        assertThat(first.apply(firstResultSet).c).isEqualTo("first c");
        assertThat(second.apply(secondResultSet).a).isEqualTo("second a");
    }

    @Test
    void compile_rebuildsAnEvictedPlanAfterThePerTypeCapacityIsExceeded() throws Exception {
        Map<List<String>, MappingPlan<EvictionRow>> plans = new LinkedHashMap<>();
        for (int i = 0; i < 256; i++) {
            List<String> labels = List.of("value", "projection" + i);
            plans.put(labels, MappingPlanCompiler.compile(EvictionRow.class, columns(labels.toArray(String[]::new))));
        }
        List<String> overflowLabels = List.of("value", "projection-overflow");
        plans.put(overflowLabels, MappingPlanCompiler.compile(EvictionRow.class,
                columns(overflowLabels.toArray(String[]::new))));

        Cache<?, ?> cache = cacheFor(EvictionRow.class);
        cache.cleanUp();
        assertThat(cache.estimatedSize()).isLessThanOrEqualTo(256);
        List<String> evictedLabels = plans.keySet().stream()
                .filter(labels -> !cache.asMap().containsKey(labels))
                .findFirst()
                .orElseThrow();
        MappingPlan<EvictionRow> rebuilt = MappingPlanCompiler.compile(EvictionRow.class,
                columns(evictedLabels.toArray(String[]::new)));
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject(1, String.class)).thenReturn("rebuilt value");

        assertThat(rebuilt).isNotSameAs(plans.get(evictedLabels));
        assertThat(rebuilt.apply(resultSet).value).isEqualTo("rebuilt value");
    }

    private static List<SelectColumn> columns(String... labels) {
        TableMeta table = TableMeta.of(CacheRow.class);
        return java.util.Arrays.stream(labels)
                .map(label -> new SelectColumn(ColumnMeta.of(table, label, String.class)))
                .toList();
    }

    private static Class<?> loadIsolatedRow() throws IOException, ClassNotFoundException {
        String className = IsolatedRow.class.getName();
        String resourceName = className.replace('.', '/') + ".class";
        byte[] bytes;
        try (InputStream stream = IsolatedRow.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IOException("Cannot load class bytes for " + className);
            }
            bytes = stream.readAllBytes();
        }
        ClassLoader loader = new ClassLoader(IsolatedRow.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(className)) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
        };
        return loader.loadClass(className);
    }

    @SuppressWarnings("unchecked")
    private static Cache<?, ?> cacheFor(Class<?> targetType) throws ReflectiveOperationException {
        Field field = MappingPlanCompiler.class.getDeclaredField("CACHE");
        field.trySetAccessible();
        ClassValue<Cache<List<String>, MappingPlan<?>>> caches =
                (ClassValue<Cache<List<String>, MappingPlan<?>>>) field.get(null);
        return caches.get(targetType);
    }

    public static class CacheRow {
        private String a;
        private String c;

        public void setA(String a) {
            this.a = a;
        }

        public void setC(String c) {
            this.c = c;
        }
    }

    public static class EvictionRow {
        private String value;

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class IsolatedRow {
    }
}
