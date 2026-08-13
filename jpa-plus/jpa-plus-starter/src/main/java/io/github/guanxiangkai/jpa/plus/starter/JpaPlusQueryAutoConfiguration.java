package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.query.compiler.DebugSqlCompiler;
import io.github.guanxiangkai.jpa.plus.query.compiler.SqlCompiler;
import io.github.guanxiangkai.jpa.plus.query.compiler.SqlCompilerRegistry;
import io.github.guanxiangkai.jpa.plus.query.executor.DefaultMutationExecutor;
import io.github.guanxiangkai.jpa.plus.query.executor.DefaultQueryExecutor;
import io.github.guanxiangkai.jpa.plus.query.executor.MutationExecutor;
import io.github.guanxiangkai.jpa.plus.query.executor.QueryContextExecutor;
import io.github.guanxiangkai.jpa.plus.query.executor.QueryExecutor;
import io.github.guanxiangkai.jpa.plus.query.pagination.CountStrategy;
import io.github.guanxiangkai.jpa.plus.query.pagination.PaginationOptimizer;
import io.github.guanxiangkai.jpa.plus.query.resolver.DefaultJpaRelationResolver;
import io.github.guanxiangkai.jpa.plus.query.resolver.JpaRelationResolver;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * JPA Plus 查询模块自动装配
 *
 * <p>负责注册：{@link SqlCompiler}（多方言）、{@link PaginationOptimizer}、
 * {@link JpaRelationResolver}、{@link QueryExecutor}。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration
public class JpaPlusQueryAutoConfiguration {

    // ─────────── SQL 编译器（多方言自动检测） ───────────

    private static String resolveJdbcUrl(DataSource ds) {
        if (ds == null) {
            throw new IllegalStateException(
                    "[jpa-plus] No DataSource available for SQL dialect detection. Configure 'jpa-plus.dialect'.");
        }
        try (Connection conn = ds.getConnection()) {
            return conn.getMetaData().getURL();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[jpa-plus] Failed to inspect DataSource JDBC URL. Configure 'jpa-plus.dialect'.", e);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    SqlCompiler sqlCompiler(
            @Value("${jpa-plus.dialect:}") String dialect,
            @Value("${jpa-plus.debug.enabled:false}") boolean debugEnabled,
            @Value("${jpa-plus.debug.print-params:true}") boolean printParams,
            ObjectProvider<DataSource> dataSourceProvider) {

        SqlCompiler compiler;
        if (!dialect.isBlank()) {
            compiler = SqlCompilerRegistry.resolveByDialect(dialect);
        } else {
            DataSource ds = dataSourceProvider.getIfAvailable();
            String jdbcUrl = resolveJdbcUrl(ds);
            compiler = SqlCompilerRegistry.resolve(jdbcUrl);
        }

        if (debugEnabled) {
            compiler = new DebugSqlCompiler(compiler, printParams);
        }
        return compiler;
    }

    // ─────────── 分页优化器 ───────────

    @Bean
    @ConditionalOnMissingBean
    PaginationOptimizer paginationOptimizer(
            @Value("${jpa-plus.pagination.count-strategy:SIMPLE}") CountStrategy countStrategy) {
        return new PaginationOptimizer(countStrategy);
    }

    // ─────────── JPA 关联解析器 ───────────

    @Bean
    @ConditionalOnMissingBean
    JpaRelationResolver jpaRelationResolver() {
        return new DefaultJpaRelationResolver();
    }

    // ─────────── 查询执行器（只读） ───────────

    @Bean
    @ConditionalOnMissingBean(QueryExecutor.class)
    DefaultQueryExecutor queryExecutor(
            EntityManager entityManager,
            SqlCompiler sqlCompiler,
            PaginationOptimizer paginationOptimizer,
            @Value("${jpa-plus.debug.slow-sql.enabled:false}") boolean slowSqlEnabled,
            @Value("${jpa-plus.debug.slow-sql.threshold:1000}") long slowSqlThresholdMs,
            @Value("${jpa-plus.query.stream.fetch-size:500}") int streamFetchSize) {
        return newQueryExecutor(entityManager, sqlCompiler, paginationOptimizer,
                slowSqlEnabled, slowSqlThresholdMs, streamFetchSize);
    }

    @Bean
    @ConditionalOnMissingBean(QueryContextExecutor.class)
    QueryContextExecutor queryContextExecutor(
            ObjectProvider<QueryExecutor> queryExecutorProvider,
            EntityManager entityManager,
            SqlCompiler sqlCompiler,
            PaginationOptimizer paginationOptimizer,
            @Value("${jpa-plus.debug.slow-sql.enabled:false}") boolean slowSqlEnabled,
            @Value("${jpa-plus.debug.slow-sql.threshold:1000}") long slowSqlThresholdMs,
            @Value("${jpa-plus.query.stream.fetch-size:500}") int streamFetchSize) {
        QueryExecutor queryExecutor = queryExecutorProvider.getIfAvailable();
        if (queryExecutor instanceof QueryContextExecutor contextExecutor) {
            return contextExecutor;
        }
        return newQueryExecutor(entityManager, sqlCompiler, paginationOptimizer,
                slowSqlEnabled, slowSqlThresholdMs, streamFetchSize);
    }

    private static DefaultQueryExecutor newQueryExecutor(EntityManager entityManager,
                                                         SqlCompiler sqlCompiler,
                                                         PaginationOptimizer paginationOptimizer,
                                                         boolean slowSqlEnabled,
                                                         long slowSqlThresholdMs,
                                                         int streamFetchSize) {
        long threshold = slowSqlEnabled ? slowSqlThresholdMs : 0L;
        return new DefaultQueryExecutor(entityManager, sqlCompiler, paginationOptimizer, threshold, streamFetchSize);
    }

    // ─────────── 写操作执行器（CQRS 分离） ───────────

    @Bean
    @ConditionalOnMissingBean
    MutationExecutor mutationExecutor(
            EntityManager entityManager,
            SqlCompiler sqlCompiler,
            @Value("${jpa-plus.debug.slow-sql.enabled:false}") boolean slowSqlEnabled,
            @Value("${jpa-plus.debug.slow-sql.threshold:1000}") long slowSqlThresholdMs) {
        long threshold = slowSqlEnabled ? slowSqlThresholdMs : 0L;
        return new DefaultMutationExecutor(entityManager, sqlCompiler, threshold);
    }
}
