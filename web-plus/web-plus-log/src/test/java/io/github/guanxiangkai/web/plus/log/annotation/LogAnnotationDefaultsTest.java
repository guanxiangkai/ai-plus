package io.github.guanxiangkai.web.plus.log.annotation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogAnnotationDefaultsTest {

    @Test
    void shouldNotPersistMethodArgumentsByDefault() throws NoSuchMethodException {
        assertThat(OperationLog.class.getMethod("saveRequestParams").getDefaultValue())
                .isEqualTo(false);
        assertThat(ScheduleLog.class.getMethod("saveParams").getDefaultValue())
                .isEqualTo(false);
        assertThat(TaskLog.class.getMethod("saveParams").getDefaultValue())
                .isEqualTo(false);
    }

    @Test
    void shouldNotPersistAiContentOrOperationResponsesByDefault() throws NoSuchMethodException {
        assertThat(OperationLog.class.getMethod("saveResponseData").getDefaultValue())
                .isEqualTo(false);
        assertThat(AiLog.class.getMethod("saveInputContent").getDefaultValue())
                .isEqualTo(false);
        assertThat(AiLog.class.getMethod("saveOutputContent").getDefaultValue())
                .isEqualTo(false);
    }
}
