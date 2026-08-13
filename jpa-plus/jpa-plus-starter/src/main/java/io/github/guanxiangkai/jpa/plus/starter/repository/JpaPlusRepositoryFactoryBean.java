package io.github.guanxiangkai.jpa.plus.starter.repository;

import io.github.guanxiangkai.jpa.plus.core.executor.JpaPlusExecutor;
import io.github.guanxiangkai.jpa.plus.query.executor.MutationExecutor;
import io.github.guanxiangkai.jpa.plus.query.executor.QueryExecutor;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

/**
 * JPA-Plus Repository 工厂 Bean
 * <p>
 * 替换 Spring Data JPA 默认的 RepositoryFactoryBean，
 * 使所有 Repository 自动获得 JPA-Plus 增强功能。
 */
public class JpaPlusRepositoryFactoryBean<R extends JpaRepository<T, ID>, T, ID>
        extends JpaRepositoryFactoryBean<R, T, ID> {

    private JpaPlusExecutor executor;
    private QueryExecutor queryExecutor;
    private MutationExecutor mutationExecutor;

    public JpaPlusRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
        super(repositoryInterface);
    }

    @Autowired
    public void setExecutors(JpaPlusExecutor executor,
                             QueryExecutor queryExecutor,
                             MutationExecutor mutationExecutor) {
        this.executor = executor;
        this.queryExecutor = queryExecutor;
        this.mutationExecutor = mutationExecutor;
    }

    @Override
    protected RepositoryFactorySupport createRepositoryFactory(EntityManager entityManager) {
        return new JpaPlusRepositoryFactory(entityManager, executor, queryExecutor, mutationExecutor);
    }
}

