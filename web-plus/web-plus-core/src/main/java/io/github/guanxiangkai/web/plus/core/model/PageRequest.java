package io.github.guanxiangkai.web.plus.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Sort;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页查询请求基类
 * <p>
 * 可被业务分页 DTO 继承，附加任意筛选字段。
 * 页码从 1 开始，默认每页 10 条。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Getter
@Setter
@NoArgsConstructor
public class PageRequest implements PageQuery, Serializable {

    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE = 1;
    /**
     * 默认每页条数
     */
    public static final int DEFAULT_SIZE = 10;
    /**
     * 最大每页条数
     */
    public static final int MAX_SIZE = 500;
    @Serial
    private static final long serialVersionUID = 1L;
    private int page = DEFAULT_PAGE;

    private int size = DEFAULT_SIZE;

    private String sortBy;

    private String sortDir = "DESC";

    private List<String> fields = new ArrayList<>();

    public PageRequest(int page, int size, String sortBy, String sortDir, List<String> fields) {
        setPage(page);
        setSize(size);
        this.sortBy = sortBy;
        setSortDir(sortDir);
        setFields(fields);
    }

    /**
     * 快捷构造
     */
    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size, null, null, null);
    }

    public void setPage(int page) {
        this.page = page <= 0 ? DEFAULT_PAGE : page;
    }

    public int getPageNum() {
        return this.page;
    }

    public void setPageNum(int pageNum) {
        setPage(pageNum);
    }

    public void setSize(int size) {
        if (size <= 0) {
            this.size = DEFAULT_SIZE;
            return;
        }
        this.size = Math.min(size, MAX_SIZE);
    }

    public int getPageSize() {
        return this.size;
    }

    public void setPageSize(int pageSize) {
        setSize(pageSize);
    }

    public void setSortDir(String sortDir) {
        this.sortDir = (sortDir == null || sortDir.isBlank()) ? "DESC" : sortDir;
    }

    public void setFields(List<String> fields) {
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
    }

    // ── PageQuery 接口实现（Lombok @Getter 生成 getX()，需手动实现无前缀 accessor）──

    @Override
    public int page() {
        return this.page;
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public String sortBy() {
        return this.sortBy;
    }

    @Override
    public String sortDir() {
        return this.sortDir;
    }

    /**
     * 0-based 偏移量（供 jpa-plus 使用）
     */
    public int offset() {
        return (page - 1) * size;
    }

    public org.springframework.data.domain.Pageable toPageable(Sort fallbackSort) {
        Sort sort = PageSorts.resolve(this, fallbackSort);
        return sort.isSorted()
                ? org.springframework.data.domain.PageRequest.of(page - 1, size, sort)
                : org.springframework.data.domain.PageRequest.of(page - 1, size);
    }

    /**
     * 是否正序
     */
    public boolean isAsc() {
        return PageQuery.super.isAsc();
    }
}
