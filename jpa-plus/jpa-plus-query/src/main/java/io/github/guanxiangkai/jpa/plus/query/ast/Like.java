package io.github.guanxiangkai.jpa.plus.query.ast;

import io.github.guanxiangkai.jpa.plus.query.metadata.ColumnMeta;

/**
 * LIKE 条件：column LIKE pattern
 */
public record Like(ColumnMeta column, String value, LikeMode mode) implements Condition {
    private static String escapeForLike(String input) {
        if (input == null) {
            return null;
        }
        // Order matters: escape the escape character first.
        return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    @Override
    public <R> R accept(ConditionVisitor<R> visitor) {
        return visitor.visit(this);
    }

    /**
     * 根据模式生成完整的 LIKE 值（已转义用户输入中的通配符）。
     *
     * <p>P1-30: Escape LIKE metacharacters in user input ({@code \}, {@code %}, {@code _})
     * before appending wildcards. Without escaping, a search for {@code "100%"} would match
     * all rows whose column starts with "100", leaking unintended data.</p>
     *
     * <p>The escape character is {@code \}. Callers must include {@code ESCAPE '\\'} in the
     * generated SQL fragment, which the SQL compiler is responsible for appending.</p>
     */
    public String pattern() {
        String escaped = escapeForLike(value);
        return switch (mode) {
            case START -> escaped + "%";
            case END -> "%" + escaped;
            case ANYWHERE -> "%" + escaped + "%";
        };
    }

    /**
     * Returns whether this LIKE pattern uses a custom escape character.
     * SQL compilers should append {@code ESCAPE '\\'} when this is true.
     */
    public boolean needsEscape() {
        return value != null && (value.contains("\\") || value.contains("%") || value.contains("_"));
    }
}

