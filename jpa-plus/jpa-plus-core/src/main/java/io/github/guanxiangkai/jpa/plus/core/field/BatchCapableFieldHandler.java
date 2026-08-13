package io.github.guanxiangkai.jpa.plus.core.field;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 批处理能力标记接口
 *
 * <p>实现此接口的 {@link FieldHandler} 声明自身已提供优化的批量处理实现。
 * 2.0 起批处理 API 只存在于该扩展接口，普通 {@link FieldHandler} 不再拥有逐条循环兜底。</p>
 *
 * <h3>设计动机</h3>
 * <ul>
 *   <li><b>性能</b>：消除每次 {@code supportsBatchProcessing()} 的反射调用（即使有缓存，
 *       首次调用仍有成本；标记接口完全零开销）</li>
 *   <li><b>明确性</b>：实现类通过 {@code implements BatchCapableFieldHandler} 显式宣告能力，
 *       不再依赖"是否覆盖了默认方法"这一脆弱约定</li>
 *   <li><b>类型安全</b>：编译期即可发现实现不一致问题</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <p>实现此接口时，必须提供真正的批处理逻辑；不需要处理的阶段应显式空实现。
 * 这种约束让编译器直接暴露不完整的批处理实现。</p>
 *
 * <pre>{@code
 * // 正确示例：声明批处理能力并提供真实实现
 * public class EncryptFieldHandler implements BatchCapableFieldHandler {
 *
 *     @Override
 *     public void beforeSaveBatch(List<?> entities, Field field) {
 *         // 批量加密，避免逐条 JCE 调用开销
 *     }
 *
 *     @Override
 *     public void afterQueryBatch(List<?> entities, Field field) {
 *         // 批量解密
 *     }
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @see FieldHandler
 * @see FieldEngine
 * @since 1.0.0
 */
public interface BatchCapableFieldHandler extends FieldHandler {

    /**
     * <p><b>实现要求</b>：实现此方法应提供真正的批量优化逻辑，
     * 而非简单地循环调用 {@link #beforeSave(Object, Field)}。</p>
     *
     * @param entities 实体列表（非空，至少 1 个元素）
     * @param field    待处理字段
     */
    void beforeSaveBatch(List<?> entities, Field field);

    /**
     * <p><b>实现要求</b>：实现此方法应提供真正的批量优化逻辑，
     * 而非简单地循环调用 {@link #afterQuery(Object, Field)}。</p>
     *
     * @param entities 实体列表（非空，至少 1 个元素）
     * @param field    待处理字段
     */
    void afterQueryBatch(List<?> entities, Field field);
}
