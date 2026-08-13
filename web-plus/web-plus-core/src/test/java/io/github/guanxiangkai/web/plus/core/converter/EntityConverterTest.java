package io.github.guanxiangkai.web.plus.core.converter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityConverterTest {

    @Test
    void toEntityShouldCopyRecordComponents() {
        TestEntity entity = EntityConverter.toEntity(new TestDto("new-name", 10), TestEntity.class);

        assertThat(entity.getName()).isEqualTo("new-name");
        assertThat(entity.getScore()).isEqualTo(10);
    }

    @Test
    void updateEntityShouldCopyRecordComponentsAndSkipNullValues() {
        TestEntity entity = new TestEntity();
        entity.setName("old-name");
        entity.setScore(1);

        EntityConverter.updateEntity(new TestDto("new-name", null), entity);

        assertThat(entity.getName()).isEqualTo("new-name");
        assertThat(entity.getScore()).isEqualTo(1);
    }

    private record TestDto(String name, Integer score) {
    }

    private static final class TestEntity {
        private String name;
        private Integer score;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }
    }
}
