package io.github.guanxiangkai.jpa.plus.query.wrapper;

import io.github.guanxiangkai.jpa.plus.query.ast.*;
import io.github.guanxiangkai.jpa.plus.query.context.OrderBy;
import io.github.guanxiangkai.jpa.plus.query.context.QueryRuntime;
import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;
import io.github.guanxiangkai.jpa.plus.query.metadata.TableMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * QueryWrapper 抽象基类
 * <p>
 * 实现通用的条件构建、排序、分页逻辑。
 *
 * @param <T> 实体类型
 */
public abstract class AbstractWrapper<T> implements QueryWrapper<T> {

    protected final Class<T> entityClass;
    protected final TableMeta tableMeta;
    private List<Condition> conditions = new ArrayList<>();
    private List<OrderBy> orderBys = new ArrayList<>();
    private List<ColumnMeta> groupBys = new ArrayList<>();
    private List<Condition> havingConditions = new ArrayList<>();
    private Integer offset;
    private Integer rows;

    protected AbstractWrapper(Class<T> entityClass) {
        this.entityClass = entityClass;
        this.tableMeta = TableMeta.of(entityClass);
    }

    /**
     * 拷贝构造器，用于 copy-on-write 模式（如 {@code limit()} 产生新实例时保留现有状态）
     */
    protected AbstractWrapper(AbstractWrapper<T> source, Integer offset, Integer rows) {
        this.entityClass = source.entityClass;
        this.tableMeta = source.tableMeta;
        this.conditions = new ArrayList<>(source.conditions);
        this.orderBys = new ArrayList<>(source.orderBys);
        this.groupBys = new ArrayList<>(source.groupBys);
        this.havingConditions = new ArrayList<>(source.havingConditions);
        this.offset = offset;
        this.rows = rows;
    }

    @Override
    public Class<T> getEntityClass() {
        return entityClass;
    }

    @Override
    public QueryWrapper<T> eq(TypedGetter<T, ?> column, Object value) {
        return compare(column, value, Eq::new);
    }

    @Override
    public QueryWrapper<T> ne(TypedGetter<T, ?> column, Object value) {
        return compare(column, value, Ne::new);
    }

    @Override
    public QueryWrapper<T> gt(TypedGetter<T, ?> column, Object value) {
        return compare(column, value, Gt::new);
    }

    @Override
    public QueryWrapper<T> ge(TypedGetter<T, ?> column, Object value) {
        return compare(column, value, Ge::new);
    }

    @Override
    public QueryWrapper<T> lt(TypedGetter<T, ?> column, Object value) {
        return compare(column, value, Lt::new);
    }

    @Override
    public QueryWrapper<T> le(TypedGetter<T, ?> column, Object value) {
        return compare(column, value, Le::new);
    }

    @Override
    public QueryWrapper<T> like(TypedGetter<T, ?> column, String value) {
        return addCondition(new Like(resolveColumn(column), value, LikeMode.ANYWHERE));
    }

    @Override
    public QueryWrapper<T> in(TypedGetter<T, ?> column, Collection<?> values) {
        return addCondition(new In(resolveColumn(column), values));
    }

    @Override
    public QueryWrapper<T> between(TypedGetter<T, ?> column, Object start, Object end) {
        return addCondition(new Between(resolveColumn(column), start, end));
    }

    @Override
    public QueryWrapper<T> and(Consumer<QueryWrapper<T>> consumer) {
        DefaultQueryWrapper<T> nested = new DefaultQueryWrapper<>(entityClass);
        consumer.accept(nested);
        Condition nestedCondition = nested.buildCondition();
        if (nestedCondition != null) {
            conditions.add(nestedCondition);
        }
        return this;
    }

    @Override
    public QueryWrapper<T> or(Consumer<QueryWrapper<T>> consumer) {
        DefaultQueryWrapper<T> nested = new DefaultQueryWrapper<>(entityClass);
        consumer.accept(nested);
        Condition rightCondition = nested.buildCondition();
        if (rightCondition != null) {
            // P0: Capture everything accumulated so far as the left side, then combine
            // via OR. Without this, or() would just append to the AND-list, making it AND.
            Condition leftCondition = buildCondition();
            conditions.clear();
            if (leftCondition != null) {
                conditions.add(new Or(leftCondition, rightCondition));
            } else {
                conditions.add(rightCondition);
            }
        }
        return this;
    }

    @Override
    public QueryWrapper<T> condition(Condition condition) {
        if (condition != null) {
            addCondition(condition);
        }
        return this;
    }

    @Override
    public QueryWrapper<T> exists(SubQuery subQuery) {
        return addCondition(new Exists(subQuery));
    }

    @Override
    public QueryWrapper<T> orderByAsc(TypedGetter<T, ?> column) {
        orderBys.add(new OrderBy(resolveColumn(column), OrderBy.Direction.ASC));
        return this;
    }

    @Override
    public QueryWrapper<T> orderByDesc(TypedGetter<T, ?> column) {
        orderBys.add(new OrderBy(resolveColumn(column), OrderBy.Direction.DESC));
        return this;
    }

    @Override
    public abstract QueryWrapper<T> limit(int offset, int rows);

    @Override
    @SuppressWarnings("unchecked")
    public QueryWrapper<T> groupBy(TypedGetter<T, ?>... columns) {
        for (TypedGetter<T, ?> col : columns) {
            groupBys.add(resolveColumn(col));
        }
        return this;
    }

    @Override
    public QueryWrapper<T> having(AggregateCondition condition) {
        if (condition != null) {
            havingConditions.add(condition);
        }
        return this;
    }

    @Override
    public Condition buildCondition() {
        if (conditions.isEmpty()) {
            return null;
        }
        if (conditions.size() == 1) {
            return conditions.getFirst();
        }
        return new And(List.copyOf(conditions));
    }

    /**
     * 解析 Lambda 为 ColumnMeta
     */
    protected ColumnMeta resolveColumn(TypedGetter<T, ?> column) {
        return LambdaColumnResolver.resolveColumn(column, tableMeta);
    }

    private QueryWrapper<T> compare(TypedGetter<T, ?> column,
                                    Object value,
                                    BiFunction<ColumnMeta, Object, Condition> factory) {
        return addCondition(factory.apply(resolveColumn(column), value));
    }

    private QueryWrapper<T> addCondition(Condition condition) {
        conditions.add(condition);
        return this;
    }

    /**
     * 构建查询运行时
     */
    protected QueryRuntime buildRuntime() {
        Condition havingCondition = null;
        if (!havingConditions.isEmpty()) {
            havingCondition = havingConditions.size() == 1
                    ? havingConditions.getFirst()
                    : new And(List.copyOf(havingConditions));
        }
        return new QueryRuntime(
                buildCondition(),
                List.copyOf(orderBys),
                Map.of(),
                offset,
                rows,
                List.copyOf(groupBys),
                havingCondition
        );
    }
}
