package io.github.guanxiangkai.jpa.plus.starter.repository;

import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.CreateTime;
import io.github.guanxiangkai.jpa.plus.field.autofill.annotation.UpdateTime;
import io.github.guanxiangkai.jpa.plus.field.id.annotation.AutoId;
import io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.annotation.LogicDelete;
import io.github.guanxiangkai.jpa.plus.interceptor.tenant.spi.TenantIdProvider;
import io.github.guanxiangkai.jpa.plus.query.wrapper.DeleteWrapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.DeleteSpecification;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 从业务消费者视角验证 JPA Plus 自动接管的标准 Repository 生命周期。
 */
@SpringBootTest(
        classes = JpaPlusRepositoryLifecycleIntegrationTest.TestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:jpa_plus_repository;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false"
        }
)
@Transactional
class JpaPlusRepositoryLifecycleIntegrationTest {

    @Autowired
    private LogicRecordRepository logicRecordRepository;

    @Autowired
    private PlainRecordRepository plainRecordRepository;

    @Autowired
    private LogicWrapperRepository logicWrapperRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Test
    void standardSaveEntrypoints_useJpaPlusFieldAndTenantLifecycle() {
        LogicRecord first = new LogicRecord();
        LogicRecord second = new LogicRecord();

        List<LogicRecord> saved = logicRecordRepository.saveAll(List.of(first, second));
        LogicRecord flushed = logicRecordRepository.saveAndFlush(new LogicRecord());

        assertThat(saved).allSatisfy(record -> {
            assertThat(record.getId()).isNotNull();
            assertThat(record.getCreatedAt()).isNotNull();
            assertThat(record.getUpdatedAt()).isNotNull();
            assertThat(record.getTenantId()).isEqualTo("tenant-1");
            assertThat(record.getDeleted()).isEqualTo(0);
        });
        assertThat(flushed.getId()).isNotNull();
        assertThat(flushed.getCreatedAt()).isNotNull();
        assertThat(flushed.getUpdatedAt()).isNotNull();
        assertThat(flushed.getTenantId()).isEqualTo("tenant-1");
    }

    @Test
    void standardDelete_logicallyDeletesAnnotatedEntityAndHidesItFromNormalQuery() {
        LogicRecord record = logicRecordRepository.saveAndFlush(new LogicRecord());

        logicRecordRepository.delete(record);
        entityManager.flush();
        entityManager.clear();

        assertThat(logicRecordRepository.findById(record.getId())).isEmpty();
        assertThat(logicRecordRepository.findAll()).isEmpty();
        Number physicalRows = (Number) entityManager.createNativeQuery("select count(*) from logic_records")
                .getSingleResult();
        assertThat(physicalRows.longValue()).isEqualTo(1L);
    }

    @Test
    void pageableQuery_filtersDeletedRowsInDatabaseAndRetainsSortingAndTotal() {
        List<LogicRecord> records = logicRecordRepository.saveAll(List.of(
                orderedRecord(1), orderedRecord(2), orderedRecord(3), orderedRecord(4), orderedRecord(5)));
        logicRecordRepository.delete(records.getFirst());
        entityManager.flush();
        entityManager.clear();

        Page<LogicRecord> page = logicRecordRepository.findAll(
                PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "sortOrder")));

        assertThat(page.getContent()).extracting(LogicRecord::getSortOrder).containsExactly(4, 5);
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(logicRecordRepository.count()).isEqualTo(4);
    }

    @Test
    void pageableQuery_returnsEmptyPageWhenEveryRecordIsLogicallyDeleted() {
        List<LogicRecord> records = logicRecordRepository.saveAll(List.of(orderedRecord(1), orderedRecord(2)));
        logicRecordRepository.deleteAll(records);
        entityManager.flush();
        entityManager.clear();

        Page<LogicRecord> page = logicRecordRepository.findAll(PageRequest.of(0, 20, Sort.by("sortOrder")));

        assertThat(page).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(logicRecordRepository.count()).isZero();
    }

    @Test
    void pageableQuery_supportsUnpagedRequestsWithoutReturningDeletedRecords() {
        List<LogicRecord> records = logicRecordRepository.saveAll(List.of(orderedRecord(1), orderedRecord(2)));
        logicRecordRepository.delete(records.get(1));
        entityManager.flush();
        entityManager.clear();

        Page<LogicRecord> page = logicRecordRepository.findAll(Pageable.unpaged());

        assertThat(page.getContent()).extracting(LogicRecord::getSortOrder).containsExactly(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void standardDelete_physicallyDeletesEntityWithoutLogicDeleteAnnotation() {
        PlainRecord record = plainRecordRepository.saveAndFlush(new PlainRecord());

        plainRecordRepository.delete(record);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(PlainRecord.class, record.getId())).isNull();
    }

    @Test
    void bulkDeleteWrapper_cannotBypassLogicDeleteLifecycle() {
        assertThatThrownBy(() -> logicWrapperRepository.delete(
                DeleteWrapper.from(LogicRecord.class).allowFullTableMutation()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("不允许批量物理删除");
    }

    @Test
    void deleteSpecification_cannotBypassLogicDeleteLifecycle() {
        assertThatThrownBy(() -> logicWrapperRepository.delete(DeleteSpecification.unrestricted()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("不允许批量物理删除");
    }

    @Test
    void repositoryFactory_isReplacedByAutoConfigurationWithoutConsumerFactorySetting() {
        String repositoryBeanName = applicationContext.getBeanNamesForType(LogicRecordRepository.class)[0];
        assertThat(applicationContext.getBeanFactory()
                .getBeanDefinition(repositoryBeanName)
                .getBeanClassName())
                .isEqualTo(JpaPlusRepositoryFactoryBean.class.getName());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {LogicRecord.class, PlainRecord.class})
    @EnableJpaRepositories(basePackageClasses = {
            LogicRecordRepository.class, LogicWrapperRepository.class, PlainRecordRepository.class
    }, considerNestedRepositories = true)
    public static class TestApplication {

        @Bean
        TenantIdProvider tenantIdProvider() {
            return () -> "tenant-1";
        }
    }

    public interface LogicRecordRepository extends JpaRepository<LogicRecord, Long> {
    }

    public interface LogicWrapperRepository extends JpaPlusRepository<LogicRecord, Long>, JpaSpecificationExecutor<LogicRecord> {
    }

    public interface PlainRecordRepository extends JpaRepository<PlainRecord, Long> {
    }

    private static LogicRecord orderedRecord(int sortOrder) {
        LogicRecord record = new LogicRecord();
        record.sortOrder = sortOrder;
        return record;
    }

    @Entity
    @Table(name = "logic_records")
    public static class LogicRecord {

        @Id
        @AutoId
        private Long id;

        @CreateTime
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @UpdateTime
        @Column(name = "updated_at")
        private LocalDateTime updatedAt;

        @Column(name = "tenant_id")
        private String tenantId;

        @LogicDelete
        private Integer deleted;

        @Column(name = "sort_order")
        private Integer sortOrder;

        Long getId() {
            return id;
        }

        LocalDateTime getCreatedAt() {
            return createdAt;
        }

        LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        String getTenantId() {
            return tenantId;
        }

        Integer getDeleted() {
            return deleted;
        }

        Integer getSortOrder() {
            return sortOrder;
        }
    }

    @Entity
    @Table(name = "plain_records")
    public static class PlainRecord {

        @Id
        @AutoId
        private Long id;

        Long getId() {
            return id;
        }
    }
}
