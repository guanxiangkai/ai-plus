package io.github.guanxiangkai.jpa.plus.starter.tenant;

import org.hibernate.resource.beans.spi.ManagedBean;

/**
 * 将 Spring 创建的对象暴露给 Hibernate Filter 参数解析 SPI。
 */
final class JpaPlusManagedBean<T> implements ManagedBean<T> {

    private final Class<T> beanClass;
    private final T beanInstance;

    JpaPlusManagedBean(Class<T> beanClass, T beanInstance) {
        this.beanClass = beanClass;
        this.beanInstance = beanInstance;
    }

    @Override
    public Class<T> getBeanClass() {
        return beanClass;
    }

    @Override
    public T getBeanInstance() {
        return beanInstance;
    }
}
