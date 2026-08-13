package io.github.guanxiangkai.jpa.plus.core.spi;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JPA-Plus SPI 加载器
 *
 * <p>通过 JDK 标准 {@link ServiceLoader} 加载实现。扩展文件路径为
 * {@code META-INF/services/<接口全限定名>}。</p>
 *
 * <p>特性：
 * <ul>
 *   <li>线程安全：使用同步 {@link WeakHashMap} 缓存已加载的实现列表</li>
 *   <li>支持排序：实现 {@link Ordered} 接口的 SPI 实例按 {@link Ordered#order()} 升序排列</li>
 *   <li>生命周期管理：{@link #invalidateAll()} 可在应用关闭时清理全部缓存，防止 ClassLoader 泄漏</li>
 * </ul>
 * </p>
 *
 * <p><b>设计模式：</b>SPI 服务发现模式 + 缓存模式</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public final class JpaPlusLoader {

    /**
     * ClassValue ties cached services to the service interface Class lifecycle and avoids the
     * global monitor previously used by Collections.synchronizedMap(WeakHashMap).
     */
    private static final ClassValue<List<?>> CACHE = new ClassValue<>() {
        @Override
        protected List<?> computeValue(Class<?> type) {
            return doLoad(type);
        }
    };

    /**
     * Tracks loaded service types so invalidateAll() can explicitly clear ClassValue entries.
     */
    private static final Set<Class<?>> LOADED_TYPES = ConcurrentHashMap.newKeySet();

    static {
        // 注册 JVM 关闭钩子，在应用关闭时自动清除 SPI 缓存，防止 ClassLoader 泄漏
        Runtime.getRuntime().addShutdownHook(
                new Thread(JpaPlusLoader::invalidateAll, "jpa-plus-spi-cleanup"));
    }

    private JpaPlusLoader() {
    }

    /**
     * 加载第一个 SPI 实现
     */
    public static <T> T load(Class<T> serviceType) {
        return loadAll(serviceType).stream().findFirst()
                .orElseThrow(() -> new JpaPlusException("No SPI implementation for: " + serviceType.getName()));
    }

    /**
     * 加载指定接口的所有 SPI 实现
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> loadAll(Class<T> serviceType) {
        Objects.requireNonNull(serviceType, "serviceType must not be null");
        LOADED_TYPES.add(serviceType);
        return (List<T>) CACHE.get(serviceType);
    }

    /**
     * 预热 SPI 缓存
     */
    public static void warmUp(Class<?>... serviceTypes) {
        for (Class<?> type : serviceTypes) {
            loadAll(type);
        }
    }

    /**
     * 使指定接口的 SPI 缓存失效
     */
    public static void invalidate(Class<?> serviceType) {
        if (serviceType == null) return;
        CACHE.remove(serviceType);
        LOADED_TYPES.remove(serviceType);
    }

    /**
     * 清除全部 SPI 缓存（应用关闭 / 热重载时调用，防止 ClassLoader 泄漏）
     */
    public static void invalidateAll() {
        for (Class<?> type : LOADED_TYPES) {
            CACHE.remove(type);
        }
        LOADED_TYPES.clear();
        log.debug("[jpa-plus] JpaPlusLoader: all SPI caches invalidated");
    }

    private static <T> List<T> doLoad(Class<T> serviceType) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = JpaPlusLoader.class.getClassLoader();
        }
        List<T> ordered = loadStandardServices(serviceType, classLoader);
        ordered.sort(Comparator.comparingInt(JpaPlusLoader::getOrder));
        return List.copyOf(ordered);
    }

    private static <T> List<T> loadStandardServices(Class<T> serviceType, ClassLoader classLoader) {
        Map<Class<?>, T> instances = new LinkedHashMap<>();
        try {
            for (T instance : ServiceLoader.load(serviceType, classLoader)) {
                instances.putIfAbsent(instance.getClass(), instance);
            }
        } catch (ServiceConfigurationError e) {
            throw new JpaPlusException(
                    "[jpa-plus] JDK ServiceLoader 加载失败: " + serviceType.getName(), e);
        }
        return new ArrayList<>(instances.values());
    }

    private static int getOrder(Object instance) {
        return instance instanceof Ordered ordered ? ordered.order() : Integer.MAX_VALUE;
    }
}
