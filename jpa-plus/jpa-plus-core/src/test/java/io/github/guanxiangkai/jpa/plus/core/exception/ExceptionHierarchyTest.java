package io.github.guanxiangkai.jpa.plus.core.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExceptionHierarchyTest {

    @Test
    void jpaPlusConfigException_isJpaPlusException() {
        var ex = new JpaPlusConfigException("bad config");
        assertThat(ex).isInstanceOf(JpaPlusException.class);
        assertThat(ex.getMessage()).isEqualTo("bad config");
    }

    @Test
    void spiLoadException_isJpaPlusException() {
        var cause = new ClassNotFoundException("missing");
        var ex = new SpiLoadException("spi failed", cause);
        assertThat(ex).isInstanceOf(JpaPlusException.class);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void fieldProcessingException_isJpaPlusException() {
        var ex = new FieldProcessingException("field error");
        assertThat(ex).isInstanceOf(JpaPlusException.class)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void catchByBase_worksForAllSubclasses() {
        assertThatThrownBy(() -> {
            throw new JpaPlusConfigException("cfg");
        })
                .isInstanceOf(JpaPlusException.class);
        assertThatThrownBy(() -> {
            throw new SpiLoadException("spi");
        })
                .isInstanceOf(JpaPlusException.class);
        assertThatThrownBy(() -> {
            throw new FieldProcessingException("field");
        })
                .isInstanceOf(JpaPlusException.class);
    }
}
