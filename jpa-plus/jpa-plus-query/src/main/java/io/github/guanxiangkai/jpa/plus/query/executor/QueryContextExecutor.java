package io.github.guanxiangkai.jpa.plus.query.executor;

import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;

import java.util.List;

/**
 * Internal executor for already-built query contexts.
 *
 * <p>This keeps the public {@link QueryExecutor} API wrapper-oriented while allowing
 * the core execution pipeline to reuse the same SQL execution implementation.</p>
 */
public interface QueryContextExecutor {

    /**
     * Execute a compiled query context and map rows to {@code resultType}.
     */
    <T> List<T> list(QueryContext context, Class<T> resultType);

    /**
     * Execute a count query for an already-built query context.
     */
    long count(QueryContext context);
}
