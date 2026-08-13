package io.github.guanxiangkai.jpa.plus.query.wrapper;

/**
 * 删除条件构造器接口
 */
public interface DeleteWrapper<T> extends QueryWrapper<T> {

    /**
     * 静态工厂方法
     */
    static <T> DeleteWrapper<T> from(Class<T> entityClass) {
        return new DefaultDeleteWrapper<>(entityClass);
    }

    /**
     * 明确声明允许无 WHERE 条件的全表 DELETE。
     *
     * <p><b>危险操作：</b>调用此方法后，执行 DELETE 时将跳过安全防护，
     * 直接删除表内所有行。请确保这是你的真实意图。</p>
     *
     * @return 当前 wrapper（链式调用）
     */
    DeleteWrapper<T> allowFullTableMutation();
}

