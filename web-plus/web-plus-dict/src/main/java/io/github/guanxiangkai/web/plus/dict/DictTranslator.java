package io.github.guanxiangkai.web.plus.dict;

import io.github.guanxiangkai.web.plus.core.annotation.DictField;
import io.github.guanxiangkai.web.plus.core.spi.ResponseTranslator;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 字典翻译器
 * <p>
 * 扫描对象（VO / DTO）中所有被 {@link DictField} 标注的字段，
 * 从 Redis（通过 {@link RedisDictStore}）读取对应 label，并回写到目标标签字段。
 * </p>
 *
 * <h3>同步用法（配合 Mono.fromCallable）</h3>
 * <pre>{@code
 * return Mono.fromCallable(() -> {
 *     UserVO vo = service.getDetail(id);
 *     dictTranslator.translate(vo);
 *     return ApiResponse.ok(vo);
 * });
 * }</pre>
 *
 * <h3>响应式管道用法</h3>
 * <pre>{@code
 * return Mono.fromCallable(() -> service.getDetail(id))
 *     .flatMap(dictTranslator::translateReactive)
 *     .map(ApiResponse::ok);
 * }</pre>
 *
 * <h3>列表翻译</h3>
 * <pre>{@code
 * return Mono.fromCallable(() -> {
 *     List<UserVO> list = service.list(query).records();
 *     dictTranslator.translateList(list);
 *     return ApiResponse.ok(list);
 * });
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 * @see DictField
 * @see RedisDictStore
 */
@Slf4j
public class DictTranslator implements ResponseTranslator {

    private final RedisDictStore dictStore;
    private final ConcurrentMap<Class<?>, List<DictBinding>> metadataCache = new ConcurrentHashMap<>();

    public DictTranslator(RedisDictStore dictStore) {
        this.dictStore = dictStore;
    }

    // ──────────────────────────── 同步 API ───────────────────────────────────────

    /**
     * 同步翻译单个对象（in-place 修改，返回同一实例）。
     * <p>适合在 {@code Mono.fromCallable()} 或普通同步代码中调用。</p>
     *
     * @param obj 需要翻译的 VO / DTO，{@code null} 时直接返回 {@code null}
     * @param <T> 对象类型
     * @return 翻译后的对象（同一实例，已回写 label）
     */
    @Override
    public <T> T translate(T obj) {
        if (obj == null) return null;
        doTranslate(obj);
        return obj;
    }

    /**
     * 同步翻译列表（in-place 修改）。
     *
     * @param list 需要翻译的 VO / DTO 列表
     * @param <T>  元素类型
     * @return 翻译后的同一列表
     */
    @Override
    public <T> List<T> translateList(List<T> list) {
        if (list == null || list.isEmpty()) return list;
        list.forEach(this::doTranslate);
        return list;
    }

    // ──────────────────────────── 响应式 API ─────────────────────────────────────

    /**
     * 响应式翻译单个对象，适合直接在 {@code flatMap} 中使用。
     *
     * @param obj 需要翻译的对象
     * @param <T> 对象类型
     * @return 翻译后对象的 {@code Mono}
     */
    public <T> Mono<T> translateReactive(T obj) {
        return Mono.fromCallable(() -> translate(obj));
    }

    /**
     * 响应式翻译列表。
     *
     * @param list 需要翻译的列表
     * @param <T>  元素类型
     * @return 翻译后列表的 {@code Mono}
     */
    public <T> Mono<List<T>> translateListReactive(List<T> list) {
        return Mono.fromCallable(() -> translateList(list));
    }

    // ──────────────────────────── 私有实现 ───────────────────────────────────────

    /**
     * 遍历对象所有字段（包含父类），找到 {@link DictField} 标注字段并翻译。
     */
    private void doTranslate(Object obj) {
        if (obj == null) return;
        Class<?> clazz = obj.getClass();

        for (DictBinding binding : metadataCache.computeIfAbsent(clazz, this::inspectBindings)) {
            try {
                Object rawValue = binding.sourceField().get(obj);
                if (rawValue == null) continue;

                String value = String.valueOf(rawValue);
                String label = dictStore.translate(binding.dictType(), value);
                writeLabel(obj, clazz, binding, label);
            } catch (IllegalAccessException e) {
                log.warn("[web-plus] 字典翻译读取字段失败: {}.{}", clazz.getSimpleName(), binding.sourceField().getName(), e);
            }
        }
    }

    /**
     * 将 label 值写入目标标签字段（向父类逐级查找）。
     */
    private void writeLabel(Object obj, Class<?> clazz, DictBinding binding, String label) {
        Field target = binding.targetField();
        if (target == null) {
            return;
        }
        try {
            target.set(obj, label);
        } catch (IllegalAccessException e) {
            log.warn("[web-plus] 字典标签写入失败: {}.{}", clazz.getSimpleName(), binding.targetFieldName(), e);
        }
    }

    /**
     * 收集类及其所有父类中被 {@link DictField} 标注的字段。
     */
    private List<DictBinding> inspectBindings(Class<?> clazz) {
        List<DictBinding> result = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                DictField dictField = f.getAnnotation(DictField.class);
                if (dictField != null) {
                    f.setAccessible(true);
                    String targetFieldName = dictField.labelField().isBlank()
                            ? f.getName() + "Label"
                            : dictField.labelField();
                    Field targetField = findField(clazz, targetFieldName);
                    if (targetField != null) {
                        targetField.setAccessible(true);
                    } else {
                        log.debug("[web-plus] 字典标签目标字段不存在，跳过: {}.{}", clazz.getSimpleName(), targetFieldName);
                    }
                    result.add(new DictBinding(f, dictField.type(), targetField, targetFieldName));
                }
            }
            current = current.getSuperclass();
        }
        return result;
    }

    /**
     * 在类及其所有父类中按名称查找字段。
     */
    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private record DictBinding(Field sourceField, String dictType, Field targetField, String targetFieldName) {
    }
}
