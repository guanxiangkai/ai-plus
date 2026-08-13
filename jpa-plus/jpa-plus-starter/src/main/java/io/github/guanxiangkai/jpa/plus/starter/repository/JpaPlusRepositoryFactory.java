package io.github.guanxiangkai.jpa.plus.starter.repository;

import io.github.guanxiangkai.jpa.plus.core.executor.JpaPlusExecutor;
import io.github.guanxiangkai.jpa.plus.query.executor.MutationExecutor;
import io.github.guanxiangkai.jpa.plus.query.executor.QueryExecutor;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;

/**
 * JPA-Plus Repository 工厂
 * <p>
 * 创建 SimpleJpaPlusRepository 实例替代默认的 SimpleJpaRepository。
 */
class JpaPlusRepositoryFactory extends JpaRepositoryFactory {

    private final EntityManager entityManager;
    private final JpaPlusExecutor executor;
    private final QueryExecutor queryExecutor;
    private final MutationExecutor mutationExecutor;

    JpaPlusRepositoryFactory(EntityManager entityManager,
                             JpaPlusExecutor executor,
                             QueryExecutor queryExecutor,
                             MutationExecutor mutationExecutor) {
        super(entityManager);
        this.entityManager = entityManager;
        this.executor = executor;
        this.queryExecutor = queryExecutor;
        this.mutationExecutor = mutationExecutor;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected SimpleJpaPlusRepository<?, ?> getTargetRepository(RepositoryInformation information, EntityManager em) {
        JpaEntityInformation<?, ?> entityInformation = getEntityInformation(information.getDomainType());
        return new SimpleJpaPlusRepository(entityInformation, em, executor, queryExecutor, mutationExecutor);
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        return SimpleJpaPlusRepository.class;
    }
}

