package io.github.guanxiangkai.jpa.plus.query.context;

import io.github.guanxiangkai.jpa.plus.query.metadata.JoinGraph;
import io.github.guanxiangkai.jpa.plus.query.metadata.TableMeta;
import io.github.guanxiangkai.jpa.plus.query.wrapper.SelectColumn;

import java.util.List;

/**
 * 查询元数据（不可变）
 *
 * @param root        根表
 * @param joinGraph   连接图
 * @param selects     SELECT 列
 * @param type        查询类型
 * @param assignments UPDATE 赋值列表
 */
public record QueryMetadata(
        TableMeta root,
        JoinGraph joinGraph,
        List<SelectColumn> selects,
        QueryType type,
        List<Assignment> assignments
) {

    /**
     * 防御性拷贝，保证 record 不可变
     */
    public QueryMetadata {
        selects = selects == null ? List.of() : List.copyOf(selects);
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
    }

    public static QueryMetadata select(TableMeta root) {
        return new QueryMetadata(root, new JoinGraph(root), List.of(), QueryType.SELECT, List.of());
    }

    public static QueryMetadata update(TableMeta root, List<Assignment> assignments) {
        return new QueryMetadata(root, new JoinGraph(root), List.of(), QueryType.UPDATE, assignments);
    }

    public static QueryMetadata delete(TableMeta root) {
        return new QueryMetadata(root, new JoinGraph(root), List.of(), QueryType.DELETE, List.of());
    }
}

