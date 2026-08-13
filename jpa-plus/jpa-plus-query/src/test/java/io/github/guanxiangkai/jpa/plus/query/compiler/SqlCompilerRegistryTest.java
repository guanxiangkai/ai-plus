package io.github.guanxiangkai.jpa.plus.query.compiler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlCompilerRegistryTest {

    @Test
    void resolve_knownJdbcUrl_returnsDialectCompiler() {
        assertThat(SqlCompilerRegistry.resolve("jdbc:postgresql://localhost:5432/app"))
                .isInstanceOf(PgSqlCompiler.class);
        assertThat(SqlCompilerRegistry.resolve("jdbc:mysql://localhost:3306/app"))
                .isInstanceOf(MySqlCompiler.class);
    }

    @Test
    void resolve_blankJdbcUrl_rejected() {
        assertThatThrownBy(() -> SqlCompilerRegistry.resolve(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JDBC URL is blank")
                .hasMessageContaining("jpa-plus.dialect");
    }

    @Test
    void resolve_unknownJdbcUrl_rejected() {
        assertThatThrownBy(() -> SqlCompilerRegistry.resolve("jdbc:unknown://localhost/app"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported JDBC URL")
                .hasMessageContaining("jdbc:mysql:");
    }

    @Test
    void resolveByDialect_unknownDialect_rejected() {
        assertThatThrownBy(() -> SqlCompilerRegistry.resolveByDialect("db2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported SQL dialect")
                .hasMessageContaining("mysql");
    }
}
