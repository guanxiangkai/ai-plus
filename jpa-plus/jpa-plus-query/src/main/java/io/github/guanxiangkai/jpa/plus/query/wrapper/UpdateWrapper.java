package io.github.guanxiangkai.jpa.plus.query.wrapper;

/**
 * 更新条件构造器接口
 */
public interface UpdateWrapper<T> extends QueryWrapper<T> {

    /**
     * 静态工厂方法
     */
    static <T> UpdateWrapper<T> from(Class<T> entityClass) {
        return new DefaultUpdateWrapper<>(entityClass);
    }

    /**
     * 设置字段值
     */
    UpdateWrapper<T> set(TypedGetter<T, ?> column, Object value);

    /**
     * 设置字段为 NULL
     */
    UpdateWrapper<T> setNull(TypedGetter<T, ?> column);

    /**
     * 明确声明允许无 WHERE 条件的全表 UPDATE。
     *
     * <p><b>危险操作：</b>调用此方法后，执行 UPDATE 时将跳过安全防护，
     * 直接更新表内所有行。请确保这是你的真实意图。</p>
     *
     * @return 当前 wrapper（链式调用）
     */
    UpdateWrapper<T> allowFullTableMutation();
}

