package io.github.guanxiangkai.web.plus.core.spi;

import java.util.List;

/**
 * 响应对象转换策略。
 *
 * <p>基础查询服务会按 {@link #order()} 顺序执行所有策略，具体能力模块可以实现
 * 字典回显、派生字段填充等转换，Web 模块无需依赖具体实现。</p>
 *
 * @author guanxiangkai
 * @since 3.1.0
 */
public interface ResponseTranslator {

    /**
     * 转换单个响应对象。
     *
     * @param value 待转换对象
     * @param <T> 对象类型
     * @return 转换结果
     */
    <T> T translate(T value);

    /**
     * 转换响应对象列表。
     *
     * @param values 待转换列表
     * @param <T> 元素类型
     * @return 转换结果
     */
    default <T> List<T> translateList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return values;
        }
        return values.stream().map(this::translate).toList();
    }

    /**
     * 策略执行顺序，数字越小越先执行。
     *
     * @return 执行顺序
     */
    default int order() {
        return 0;
    }
}
