package io.github.guanxiangkai.web.plus.web.aot;

import io.github.guanxiangkai.web.plus.web.controller.BaseController;
import io.github.guanxiangkai.web.plus.web.repository.BaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class WebNativeConfigurationTest {

    @Test
    void registrarShouldRegisterWebPublicBoundaries() {
        RuntimeHints hints = new RuntimeHints();

        new WebNativeConfiguration.Registrar().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(BaseController.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(BaseRepository.class).test(hints)).isTrue();
    }
}
