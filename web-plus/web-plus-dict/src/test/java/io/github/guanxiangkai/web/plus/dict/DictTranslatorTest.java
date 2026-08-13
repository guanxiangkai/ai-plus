package io.github.guanxiangkai.web.plus.dict;

import io.github.guanxiangkai.web.plus.core.annotation.DictField;
import io.github.guanxiangkai.web.plus.core.spi.ResponseTranslator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DictTranslatorTest {

    @Test
    void translate_reusesCachedMetadataAcrossSameType() throws Exception {
        RedisDictStore dictStore = mock(RedisDictStore.class);
        when(dictStore.translate("sys_status", "1")).thenReturn("启用");
        when(dictStore.translate("sys_gender", "M")).thenReturn("男");
        when(dictStore.translate("sys_status", "0")).thenReturn("禁用");
        when(dictStore.translate("sys_gender", "F")).thenReturn("女");

        DictTranslator translator = new DictTranslator(dictStore);
        assertThat(translator).isInstanceOf(ResponseTranslator.class);

        UserView first = new UserView();
        first.setStatus("1");
        first.setGender("M");
        translator.translate(first);

        UserView second = new UserView();
        second.setStatus("0");
        second.setGender("F");
        translator.translate(second);

        assertThat(first.getStatusLabel()).isEqualTo("启用");
        assertThat(first.getGenderText()).isEqualTo("男");
        assertThat(second.getStatusLabel()).isEqualTo("禁用");
        assertThat(second.getGenderText()).isEqualTo("女");

        Field cacheField = DictTranslator.class.getDeclaredField("metadataCache");
        cacheField.setAccessible(true);
        Map<?, ?> cache = (Map<?, ?>) cacheField.get(translator);
        assertThat(cache).hasSize(1);
        org.junit.jupiter.api.Assertions.assertTrue(cache.containsKey(UserView.class));
    }

    static class BaseView {
        @DictField(type = "sys_status")
        private String status;
        private String statusLabel;

        void setStatus(String status) {
            this.status = status;
        }

        String getStatusLabel() {
            return statusLabel;
        }
    }

    static class UserView extends BaseView {
        @DictField(type = "sys_gender", labelField = "genderText")
        private String gender;
        private String genderText;

        void setGender(String gender) {
            this.gender = gender;
        }

        String getGenderText() {
            return genderText;
        }
    }
}
