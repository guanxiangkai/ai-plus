package io.github.guanxiangkai.jpa.plus.core.field;

import io.github.guanxiangkai.jpa.plus.core.spi.Ordered;

import java.lang.reflect.Field;

/**
 * 字段处理器接口
 *
 * <p>对实体字段进行增强处理的 SPI 扩展点。框架内置以下实现：
 * <ul>
 *   <li>IdFieldHandler —— 主键自动生成（雪花/UUID/自定义）</li>
 *   <li>AutoFillFieldHandler —— 自动填充（createTime/updateTime/createBy/updateBy）</li>
 *   <li>EncryptFieldHandler —— 字段加密/解密（版本化密钥轮换）</li>
 *   <li>DictFieldHandler —— 字典标签回写（批量查询优化）</li>
 *   <li>DesensitizeFieldHandler —— 字段脱敏</li>
 *   <li>SensitiveWordHandler —— 敏感词检测</li>
 *   <li>VersionFieldHandler —— 乐观锁版本自增</li>
 *   <li>LogicDeleteFieldHandler —— 逻辑删除标记</li>
 * </ul>
 * </p>
 *
 * <p>2.0 起，批处理能力不再通过默认方法兜底；需要批量优化的处理器必须显式实现
 * {@link BatchCapableFieldHandler}。这样可以避免隐式逐条循环伪装成批处理能力。</p>
 *
 * <p><b>设计模式：</b>
 * <ul>
 *   <li>策略模式（Strategy） —— 每个实现封装一种字段处理策略</li>
 *   <li>扩展接口模式（Extension Interface） —— {@link BatchCapableFieldHandler} 声明批处理能力</li>
 * </ul>
 * </p>
 *
 * @author guanxiangkai
 * @see FieldEngine
 * @see BatchCapableFieldHandler
 * @since 1.0.0
 */
public interface FieldHandler extends Ordered {

    /**
     * 判断是否支持处理指定字段（通常检测字段上的注解）
     *
     * @param field 实体字段
     * @return 支持返回 {@code true}
     */
    boolean supports(Field field);

    // ═══════════════════════════ 单实体处理 API ═══════════════════════════

    /**
     * 保存前处理单个实体（如加密、敏感词检测、版本自增）
     *
     * @param entity 实体对象
     * @param field  待处理字段
     */
    default void beforeSave(Object entity, Field field) {
        // 默认空实现
    }

    /**
     * 查询后处理单个实体（如解密、字典回写、脱敏）
     *
     * @param entity 实体对象
     * @param field  待处理字段
     */
    default void afterQuery(Object entity, Field field) {
        // 默认空实现
    }

}
