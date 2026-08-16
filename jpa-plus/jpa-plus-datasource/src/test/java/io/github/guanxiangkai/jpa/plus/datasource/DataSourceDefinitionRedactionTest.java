package io.github.guanxiangkai.jpa.plus.datasource;

import io.github.guanxiangkai.jpa.plus.datasource.model.DataSourceDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceDefinitionRedactionTest {

    @Test
    void shouldRedactConnectionDetailsFromStringRepresentation() {
        DataSourceDefinition definition = DataSourceDefinition.of(
                "primary",
                "jdbc:postgresql://database.internal:5432/application",
                "database-user",
                "database-password"
        );

        assertThat(definition.toString())
                .contains("connection=<redacted>")
                .doesNotContain(
                        "database.internal", "application", "database-user", "database-password");
    }
}
