package io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.interceptor;

import io.github.guanxiangkai.jpa.plus.core.interceptor.Chain;
import io.github.guanxiangkai.jpa.plus.core.interceptor.DataInterceptor;
import io.github.guanxiangkai.jpa.plus.core.interceptor.Phase;
import io.github.guanxiangkai.jpa.plus.core.model.DataInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;
import io.github.guanxiangkai.jpa.plus.core.model.QueryInvocation;
import io.github.guanxiangkai.jpa.plus.core.util.NamingUtils;
import io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.annotation.LogicDelete;
import io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.handler.LogicDeleteFieldHandler;
import io.github.guanxiangkai.jpa.plus.query.ast.Condition;
import io.github.guanxiangkai.jpa.plus.query.ast.Conditions;
import io.github.guanxiangkai.jpa.plus.query.ast.Eq;
import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;
import io.github.guanxiangkai.jpa.plus.query.context.QueryRuntime;
import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.Optional;


/**
 * 逻辑删除拦截器
 *
 * <p>自动扫描实体类中标注了 {@link LogicDelete} 的字段，根据字段类型推导
 * 未删除值和列名，在查询/删除时注入对应条件到 AST。</p>
 *
 * <p><b>无需任何构造参数</b> —— 所有信息从注解 + 字段类型自动获取。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class LogicDeleteInterceptor implements DataInterceptor {

    /**
     * 缓存每个实体类的逻辑删除元数据
     */
    private final ClassValue<Optional<LogicDeleteMeta>> logicDeleteMetaCache = new ClassValue<>() {
        @Override
        protected Optional<LogicDeleteMeta> computeValue(Class<?> type) {
            return resolveLogicDeleteMeta(type);
        }
    };

    @Override
    public int order() {
        return 200;
    }

    @Override
    public Phase phase() {
        return Phase.BEFORE;
    }

    @Override
    public boolean supports(OperationType type) {
        // LogicDeleteInterceptor only rewrites QUERY conditions to exclude soft-deleted rows.
        // DELETE invocations are intentionally not intercepted here: converting a hard delete
        // into a soft delete (UPDATE deleted_flag) requires write access to the entity,
        // which is handled by LogicDeleteFieldHandler on the SAVE path.
        // Claiming support for DELETE without actually doing anything would be misleading.
        return type == OperationType.QUERY;
    }

    @Override
    public Object intercept(DataInvocation invocation, Chain chain) throws Throwable {
        if (invocation instanceof QueryInvocation qi && qi.queryContext() instanceof QueryContext ctx) {
            Optional<LogicDeleteMeta> metaOpt = logicDeleteMetaCache.get(qi.entityClass());

            if (metaOpt.isPresent()) {
                LogicDeleteMeta meta = metaOpt.get();
                Condition logicDeleteCondition = new Eq(
                        ColumnMeta.of(ctx.metadata().root(), meta.columnName(), meta.fieldType()),
                        meta.notDeletedValue()
                );
                Condition combined = Conditions.and(ctx.runtime().where(), logicDeleteCondition);
                QueryRuntime newRuntime = ctx.runtime().withWhere(combined);
                invocation = qi.withQueryContext(ctx.withRuntime(newRuntime));
            }
        }
        return chain.proceed(invocation);
    }

    /**
     * 扫描实体类（含父类），查找 @LogicDelete 注解字段
     */
    private Optional<LogicDeleteMeta> resolveLogicDeleteMeta(Class<?> entityClass) {
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                LogicDelete annotation = field.getAnnotation(LogicDelete.class);
                if (annotation != null) {
                    String columnName = NamingUtils.camelToSnake(field.getName());
                    Object notDeletedValue = LogicDeleteFieldHandler.resolveNotDeletedValue(field.getType(), annotation);

                    if (log.isDebugEnabled()) {
                        log.debug("LogicDelete resolved: entity={}, column={}, type={}, notDeletedValue={}",
                                entityClass.getSimpleName(), columnName, field.getType().getSimpleName(), notDeletedValue);
                    }
                    return Optional.of(new LogicDeleteMeta(columnName, field.getType(), notDeletedValue));
                }
            }
            current = current.getSuperclass();
        }
        return Optional.empty();
    }

    /**
     * 逻辑删除元数据
     */
    private record LogicDeleteMeta(String columnName, Class<?> fieldType, Object notDeletedValue) {
    }
}
