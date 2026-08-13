package io.github.guanxiangkai.jpa.plus.query.compiler;

import io.github.guanxiangkai.jpa.plus.query.ast.*;
import io.github.guanxiangkai.jpa.plus.query.context.Assignment;
import io.github.guanxiangkai.jpa.plus.query.context.QueryContext;
import io.github.guanxiangkai.jpa.plus.query.context.QueryRuntime;
import io.github.guanxiangkai.jpa.plus.query.context.QueryType;
import io.github.guanxiangkai.jpa.plus.query.metadata.JoinNode;
import io.github.guanxiangkai.jpa.plus.query.metadata.TableMeta;
import io.github.guanxiangkai.jpa.plus.query.normalizer.ConditionNormalizer;
import io.github.guanxiangkai.jpa.plus.query.normalizer.ParameterNamingStrategy;
import io.github.guanxiangkai.jpa.plus.query.wrapper.SelectColumn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * SQL 编译器抽象基类（模板方法模式）
 *
 * <p>封装所有 SQL 方言共享的编译逻辑（SELECT/UPDATE/DELETE 构建、FROM/JOIN 构建、
 * WHERE/ORDER BY 构建、条件 AST 访问者实现），子类仅需实现方言差异部分。</p>
 *
 * <p>编译过程中使用局部变量 {@code sql} 和 {@code params}，
 * 而非实例字段，保证线程安全（单例 Bean 可安全用于并发编译）。</p>
 *
 * <p><b>设计模式：</b>
 * <ul>
 *   <li>模板方法模式（Template Method） —— 定义编译骨架，子类覆盖方言差异</li>
 *   <li>访问者模式（Visitor） —— 遍历条件 AST 生成 SQL 片段</li>
 * </ul>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public abstract class AbstractSqlCompiler implements SqlCompiler {

    /**
     * P1-11: Allowlist pattern for SQL identifiers (column aliases, etc.).
     * Only accepts names matching {@code [a-zA-Z_][a-zA-Z0-9_]*} to prevent SQL injection
     * when user-supplied alias strings are concatenated directly into SQL.
     */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * Validates that {@code name} is a safe SQL identifier (letters, digits, underscores only;
     * must not start with a digit). Throws {@link io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException}
     * if the name fails validation to prevent SQL injection via user-supplied column aliases.
     */
    private static void validateSqlIdentifier(String name, String context) {
        if (name == null || !SAFE_IDENTIFIER.matcher(name).matches()) {
            throw new io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException(
                    "Invalid SQL identifier in " + context + ": '" + name +
                            "'. Only letters, digits, and underscores are allowed, " +
                            "and the name must not start with a digit.");
        }
    }

    @Override
    public SqlResult compile(QueryContext ctx) {
        return ParameterNamingStrategy.runWhere(() -> doCompile(ctx));
    }

    /**
     * Compile a sub-query within the CURRENT {@link ParameterNamingStrategy} scope.
     *
     * <p>Unlike {@link #compile(QueryContext)} this method does NOT start a new {@code runWhere()}
     * scope, so the subquery shares the parent's parameter counter, guaranteeing unique parameter
     * names across the entire query tree (prevents P0-18 param collision).</p>
     */
    SqlResult compileInContext(QueryContext ctx) {
        return doCompile(ctx);
    }

    private SqlResult doCompile(QueryContext ctx) {
        // 使用局部变量，保证线程安全
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        ConditionNormalizer normalizer = new ConditionNormalizer();

        // 0. 规范化条件
        Condition normalizedWhere = ctx.runtime().where() != null
                ? ctx.runtime().where().accept(normalizer)
                : null;
        QueryRuntime newRuntime = ctx.runtime().withWhere(normalizedWhere);
        QueryContext normalizedCtx = ctx.withRuntime(newRuntime);

        // 1. 构建语句头部
        switch (normalizedCtx.type()) {
            case SELECT -> buildSelect(sql, normalizedCtx);
            case UPDATE -> buildUpdate(sql, normalizedCtx);
            case DELETE -> buildDelete(sql, normalizedCtx);
            default -> throw new IllegalStateException("Unsupported QueryType: " + normalizedCtx.type());
        }

        // 2. 处理 FROM / JOIN
        buildFrom(sql, normalizedCtx);

        // 3. 处理 SET（UPDATE）
        if (normalizedCtx.type() == QueryType.UPDATE) {
            buildSet(sql, params, normalizedCtx);
        }

        // 4. 处理 WHERE
        if (normalizedCtx.runtime().where() != null) {
            sql.append(" WHERE ");
            visitCondition(sql, params, normalizedCtx.runtime().where());
        }

        // 4.5 处理 GROUP BY
        if (!normalizedCtx.runtime().groupBys().isEmpty()) {
            sql.append(" GROUP BY ");
            StringJoiner groupSj = new StringJoiner(", ");
            for (var col : normalizedCtx.runtime().groupBys()) {
                groupSj.add(col.qualifiedName());
            }
            sql.append(groupSj);
        }

        // 4.6 处理 HAVING（需在 GROUP BY 之后）
        if (normalizedCtx.runtime().having() != null) {
            sql.append(" HAVING ");
            visitCondition(sql, params, normalizedCtx.runtime().having());
        }

        // 5. 处理 ORDER BY
        if (!normalizedCtx.runtime().orderBys().isEmpty()) {
            sql.append(" ORDER BY ");
            StringJoiner orderSj = new StringJoiner(", ");
            for (var ob : normalizedCtx.runtime().orderBys()) {
                orderSj.add(ob.column().qualifiedName() + " " + ob.direction().name());
            }
            sql.append(orderSj);
        }

        // 6. 处理 LIMIT（方言差异点 —— 由子类实现）
        appendLimit(sql, normalizedCtx.runtime().offset(), normalizedCtx.runtime().rows());

        return new SqlResult(sql.toString(), Map.copyOf(params));
    }

    // ─────────── 方言差异扩展点 ───────────

    /**
     * 追加分页语法（子类必须实现）
     *
     * @param sql    SQL 构建器
     * @param offset 偏移量（可能为 {@code null}）
     * @param rows   行数（可能为 {@code null}）
     */
    protected abstract void appendLimit(StringBuilder sql, Integer offset, Integer rows);

    /**
     * 创建当前方言的新编译器实例（用于子查询编译）
     */
    protected abstract AbstractSqlCompiler newInstance();

    // ─────────── SQL 构建方法 ───────────

    private void buildSelect(StringBuilder sql, QueryContext ctx) {
        sql.append("SELECT ");
        List<SelectColumn> selects = ctx.metadata().selects();
        if (selects == null || selects.isEmpty()) {
            sql.append(ctx.metadata().root().alias()).append(".*");
        } else {
            StringJoiner sj = new StringJoiner(", ");
            for (SelectColumn sc : selects) {
                String qn = sc.column().qualifiedName();
                String alias = sc.column().alias();
                if (alias != null && !alias.equals(sc.column().columnName())) {
                    validateSqlIdentifier(alias, "SELECT alias for column '" + sc.column().columnName() + "'");
                    sj.add(qn + " AS " + alias);
                } else {
                    sj.add(qn);
                }
            }
            sql.append(sj);
        }
    }

    private void buildUpdate(StringBuilder sql, QueryContext ctx) {
        sql.append("UPDATE ");
        TableMeta root = ctx.metadata().root();
        sql.append(root.tableName()).append(" ").append(root.alias());
    }

    private void buildDelete(StringBuilder sql, QueryContext ctx) {
        sql.append("DELETE ");
        sql.append(ctx.metadata().root().alias());
    }

    private void buildFrom(StringBuilder sql, QueryContext ctx) {
        TableMeta root = ctx.metadata().root();
        if (ctx.type() != QueryType.UPDATE) {
            sql.append(" FROM ").append(root.tableName()).append(" ").append(root.alias());
        }

        // JOIN
        if (ctx.metadata().joinGraph() != null && ctx.metadata().joinGraph().hasJoins()) {
            for (JoinNode node : ctx.metadata().joinGraph().nodes()) {
                sql.append(" ").append(node.type().name()).append(" JOIN ");
                sql.append(node.table().tableName()).append(" ").append(node.table().alias());
                sql.append(" ON ");
                sql.append(node.condition().left().qualifiedName());
                sql.append(" = ");
                sql.append(node.condition().right().qualifiedName());
            }
        }
    }

    private void buildSet(StringBuilder sql, Map<String, Object> params, QueryContext ctx) {
        List<Assignment> assignments = ctx.metadata().assignments();
        if (assignments != null && !assignments.isEmpty()) {
            sql.append(" SET ");
            StringJoiner sj = new StringJoiner(", ");
            for (Assignment a : assignments) {
                if (a.value() == null) {
                    sj.add(a.column().qualifiedName() + " = NULL");
                } else {
                    String paramName = ParameterNamingStrategy.next("set");
                    params.put(paramName, a.value());
                    sj.add(a.column().qualifiedName() + " = :" + paramName);
                }
            }
            sql.append(sj);
        }
    }

    // ─────────── 条件访问者（线程安全的局部访问） ───────────

    private void visitCondition(StringBuilder sql, Map<String, Object> params, Condition condition) {
        switch (condition) {
            case Eq eq -> {
                String p = ParameterNamingStrategy.next("eq");
                sql.append(eq.column().qualifiedName()).append(" = :").append(p);
                params.put(p, eq.value());
            }
            case Ne ne -> {
                String p = ParameterNamingStrategy.next("ne");
                sql.append(ne.column().qualifiedName()).append(" <> :").append(p);
                params.put(p, ne.value());
            }
            case Gt gt -> {
                String p = ParameterNamingStrategy.next("gt");
                sql.append(gt.column().qualifiedName()).append(" > :").append(p);
                params.put(p, gt.value());
            }
            case Ge ge -> {
                String p = ParameterNamingStrategy.next("ge");
                sql.append(ge.column().qualifiedName()).append(" >= :").append(p);
                params.put(p, ge.value());
            }
            case Lt lt -> {
                String p = ParameterNamingStrategy.next("lt");
                sql.append(lt.column().qualifiedName()).append(" < :").append(p);
                params.put(p, lt.value());
            }
            case Le le -> {
                String p = ParameterNamingStrategy.next("le");
                sql.append(le.column().qualifiedName()).append(" <= :").append(p);
                params.put(p, le.value());
            }
            case Like like -> {
                String p = ParameterNamingStrategy.next("like");
                sql.append(like.column().qualifiedName()).append(" LIKE :").append(p);
                // P1-30: Append ESCAPE clause when the pattern contains escaped metacharacters.
                if (like.needsEscape()) {
                    sql.append(" ESCAPE '\\\\'");
                }
                params.put(p, like.pattern());
            }
            case In in -> {
                String p = ParameterNamingStrategy.next("in");
                sql.append(in.column().qualifiedName()).append(" IN (:").append(p).append(")");
                params.put(p, in.values());
            }
            case Between between -> {
                String sp = ParameterNamingStrategy.next("between_start");
                String ep = ParameterNamingStrategy.next("between_end");
                sql.append(between.column().qualifiedName())
                        .append(" BETWEEN :").append(sp).append(" AND :").append(ep);
                params.put(sp, between.start());
                params.put(ep, between.end());
            }
            case And and -> {
                sql.append("(");
                List<Condition> conditions = and.conditions();
                for (int i = 0; i < conditions.size(); i++) {
                    if (i > 0) sql.append(" AND ");
                    visitCondition(sql, params, conditions.get(i));
                }
                sql.append(")");
            }
            case Or or -> {
                sql.append("(");
                List<Condition> conditions = or.conditions();
                for (int i = 0; i < conditions.size(); i++) {
                    if (i > 0) sql.append(" OR ");
                    visitCondition(sql, params, conditions.get(i));
                }
                sql.append(")");
            }
            case Not not -> {
                sql.append("NOT (");
                visitCondition(sql, params, not.condition());
                sql.append(")");
            }
            case Exists exists -> {
                sql.append("EXISTS (");
                // P0-18: Use compileInContext() to share the parent's parameter counter,
                // preventing duplicate names like eq_0 in both parent and subquery.
                SqlResult subResult = newInstance().compileInContext(exists.subQuery().query());
                sql.append(subResult.sql());
                params.putAll(subResult.params());
                sql.append(")");
            }
            case SubQuery subQuery -> {
                sql.append("(");
                // P0-18: Same fix — reuse parent's counter via compileInContext().
                SqlResult subResult = newInstance().compileInContext(subQuery.query());
                sql.append(subResult.sql());
                params.putAll(subResult.params());
                sql.append(")");
            }
            case AggregateCondition agg -> {
                String colName = agg.column() != null ? agg.column().qualifiedName() : null;
                String aggExpr = agg.function().toSql(colName);
                String p = ParameterNamingStrategy.next("agg");
                String op = switch (agg.operator()) {
                    case EQ -> "=";
                    case NE -> "<>";
                    case GT -> ">";
                    case GE -> ">=";
                    case LT -> "<";
                    case LE -> "<=";
                    default -> throw new IllegalArgumentException("Unsupported aggregate operator: " + agg.operator());
                };
                sql.append(aggExpr).append(" ").append(op).append(" :").append(p);
                params.put(p, agg.value());
            }
        }
    }
}

