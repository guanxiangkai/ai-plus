package io.github.guanxiangkai.jpa.plus.query.resolver;

import io.github.guanxiangkai.jpa.plus.query.metadata.JoinNode;
import io.github.guanxiangkai.jpa.plus.query.metadata.TableMeta;

import java.util.List;

/**
 * JPA 关联解析器接口
 * <p>
 * 解析实体上的 JPA 关联注解（@OneToMany, @ManyToOne, @ManyToMany 等），
 * 自动推断 Join 条件。
 */
public interface JpaRelationResolver {

    /**
     * 解析实体类的所有关联关系
     *
     * @param entityClass 实体类
     * @param tableMeta   表元数据
     * @return Join 节点列表
     */
    List<JoinNode> resolve(Class<?> entityClass, TableMeta tableMeta);

    /**
     * 根据关系属性名解析单个关联
     *
     * @param entityClass  实体类
     * @param propertyName 关系属性名（如 "orders"）
     * @param tableMeta    源表元数据
     * @return Join 节点
     */
    JoinNode resolveProperty(Class<?> entityClass, String propertyName, TableMeta tableMeta);
}

