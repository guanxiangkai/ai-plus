package io.github.guanxiangkai.jpa.plus.interceptor.orderby.interceptor;

import io.github.guanxiangkai.jpa.plus.core.interceptor.Chain;
import io.github.guanxiangkai.jpa.plus.core.interceptor.DataInterceptor;
import io.github.guanxiangkai.jpa.plus.core.interceptor.Phase;
import io.github.guanxiangkai.jpa.plus.core.model.DataInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;
import io.github.guanxiangkai.jpa.plus.core.model.QueryInvocation;
import io.github.guanxiangkai.jpa.plus.core.util.NamingUtils;
import io.github.guanxiangkai.jpa.plus.interceptor.orderby.annotation.AutoOrderBy;
import io.github.guanxiangkai.jpa.plus.interceptor.orderby.enums.Direction;
import io.github.guanxiangkai.jpa.plus.query.context.OrderBy;
import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;
import io.github.guanxiangkai.jpa.plus.query.context.QueryRuntime;
import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

/**
 * 自动排序拦截器
 *
 * <p>在查询操作前检测是否已有显式排序，若无则根据实体类上的 {@link AutoOrderBy}
 * 注解自动注入默认 ORDER BY 子句到 AST。</p>
 *
 * <p><b>执行时机：</b>BEFORE 阶段，order = 250（在逻辑删除之后、核心执行之前）</p>
 *
 * <p><b>核心逻辑：</b>
 * <ol>
 *   <li>仅对 QUERY 操作生效</li>
 *   <li>若 {@link QueryRuntime#orderBys()} 非空（用户已显式指定排序），直接跳过</li>
 *   <li>扫描实体类（含父类）中标注了 {@code @AutoOrderBy} 的字段和类级注解</li>
 *   <li>按 {@link AutoOrderBy#priority()} 排序后，构建 {@link OrderBy} 列表</li>
 *   <li>将排序列表注入 {@link QueryRuntime}，生成新的 {@link QueryContext}</li>
 * </ol>
 * </p>
 *
 * <p><b>设计模式：</b>责任链模式（Chain of Responsibility） —— 作为拦截器链中的一环</p>
 *
 * @author guanxiangkai
 * @see AutoOrderBy
 * @since 1.0.0
 */
@Slf4j
public class AutoOrderByInterceptor implements DataInterceptor {

    /**
     * 缓存每个实体类的默认排序规则（避免重复反射扫描）
     */
    private final ClassValue<List<OrderByMeta>> orderByMetaCache = new ClassValue<>() {
        @Override
        protected List<OrderByMeta> computeValue(Class<?> type) {
            return resolveOrderByMetas(type);
        }
    };

    @Override
    public int order() {
        return 250;
    }

    @Override
    public Phase phase() {
        return Phase.BEFORE;
    }

    @Override
    public boolean supports(OperationType type) {
        return type == OperationType.QUERY;
    }

    @Override
    public Object intercept(DataInvocation invocation, Chain chain) throws Throwable {
        if (invocation instanceof QueryInvocation qi && qi.queryContext() instanceof QueryContext ctx) {
            // 若已有显式排序，跳过
            if (!ctx.runtime().orderBys().isEmpty()) {
                return chain.proceed(invocation);
            }

            // 获取实体类的默认排序配置
            List<OrderByMeta> metas = orderByMetaCache.get(qi.entityClass());

            if (!metas.isEmpty()) {
                List<OrderBy> defaultOrderBys = metas.stream()
                        .map(meta -> new OrderBy(
                                ColumnMeta.of(ctx.metadata().root(), meta.columnName(),
                                        meta.field() != null ? meta.field().getType() : Object.class),
                                meta.direction() == Direction.ASC
                                        ? OrderBy.Direction.ASC
                                        : OrderBy.Direction.DESC
                        ))
                        .toList();

                QueryRuntime newRuntime = ctx.runtime().withOrderBys(defaultOrderBys);
                invocation = qi.withQueryContext(ctx.withRuntime(newRuntime));

                if (log.isDebugEnabled()) {
                    log.debug("AutoOrderBy: entity={}, orders={}",
                            qi.entityClass().getSimpleName(),
                            metas.stream().map(m -> m.columnName() + " " + m.direction()).toList());
                }
            }
        }
        return chain.proceed(invocation);
    }

    /**
     * 扫描实体类及其父类，提取所有 @AutoOrderBy 注解信息
     *
     * <p>支持两种标注位置：
     * <ul>
     *   <li><b>字段级：</b>列名从字段名推导（驼峰转蛇形），或使用 {@code column()} 覆盖</li>
     *   <li><b>类级：</b>必须指定 {@code column()}，支持 {@code @Repeatable} 多次标注</li>
     * </ul>
     * 两种方式可混用，最终按 {@code priority()} 统一排序。</p>
     */
    private List<OrderByMeta> resolveOrderByMetas(Class<?> entityClass) {
        List<OrderByMeta> metas = new java.util.ArrayList<>();
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            // ① 扫描字段级 @AutoOrderBy
            for (Field field : current.getDeclaredFields()) {
                AutoOrderBy annotation = field.getAnnotation(AutoOrderBy.class);
                if (annotation != null) {
                    String columnName = annotation.column().isEmpty()
                            ? NamingUtils.camelToSnake(field.getName())
                            : annotation.column();
                    metas.add(new OrderByMeta(field, columnName, annotation.direction(), annotation.priority()));
                }
            }
            // ② 扫描类级 @AutoOrderBy（@Repeatable 支持多个）
            AutoOrderBy[] classAnnotations = current.getAnnotationsByType(AutoOrderBy.class);
            for (AutoOrderBy annotation : classAnnotations) {
                if (!annotation.column().isEmpty()) {
                    metas.add(new OrderByMeta(null, annotation.column(), annotation.direction(), annotation.priority()));
                } else {
                    log.warn("AutoOrderBy on class {} missing 'column', skipped", current.getSimpleName());
                }
            }
            current = current.getSuperclass();
        }
        // 按 priority 排序
        metas.sort(Comparator.comparingInt(OrderByMeta::priority));
        return List.copyOf(metas);
    }

    /**
     * 排序元数据（内部缓存用）
     *
     * @param field      来源字段（类级注解时为 {@code null}）
     * @param columnName 数据库列名
     * @param direction  排序方向
     * @param priority   排序优先级
     */
    private record OrderByMeta(
            Field field,
            String columnName,
            Direction direction,
            int priority
    ) {
    }
}
