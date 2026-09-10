package io.github.guanxiangkai.web.plus.core.domain.vo;

import io.github.guanxiangkai.jpa.plus.field.id.annotation.AutoId;
import io.github.guanxiangkai.web.plus.core.entity.BaseEntity;
import io.github.guanxiangkai.web.plus.core.model.Identifiable;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.beans.Introspector;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class BaseVOInheritanceTest {

    @Test
    void keepsSingleJavaBeansIdPropertyAndJsonShape() throws Exception {
        SampleVO value = new SampleVO();
        value.setId("id-1");

        long idPropertyCount = Arrays.stream(Introspector.getBeanInfo(SampleVO.class).getPropertyDescriptors())
                .filter(property -> property.getName().equals("id"))
                .count();
        String json = new ObjectMapper().writeValueAsString(value);

        assertThat(idPropertyCount).isEqualTo(1);
        assertThat(json).containsOnlyOnce("\"id\"");
        assertThat(json).contains("\"createTime\"", "\"updateTime\"");
        assertThat(BaseVO.class.getDeclaredFields()).extracting(Field::getName).doesNotContain("id");
        assertThat(BasePageVO.class.getDeclaredFields()).extracting(Field::getName).contains("id");
        assertThat(BaseVO.class.getConstructor()).isNotNull();
    }

    @Test
    void preservesValueEqualityAndEntityIdStorageAnnotations() throws Exception {
        SampleVO first = new SampleVO();
        first.setId("id-1");
        SampleVO second = new SampleVO();
        second.setId("id-1");
        Field id = BaseEntity.class.getDeclaredField("id");

        assertThat(first).isEqualTo(second);
        assertThat(Identifiable.class).isAssignableFrom(BaseEntity.class);
        assertThat(Identifiable.class).isAssignableFrom(BaseVO.class);
        assertThat(Identifiable.class).isAssignableFrom(BasePageVO.class);
        assertThat(id.isAnnotationPresent(Id.class)).isTrue();
        assertThat(id.isAnnotationPresent(AutoId.class)).isTrue();
        assertThat(id.isAnnotationPresent(Column.class)).isTrue();
        assertThat(id.getAnnotation(Column.class).name()).isEqualTo("id");
    }

    private static final class SampleVO extends BaseVO {
    }
}
