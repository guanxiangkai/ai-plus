package io.github.guanxiangkai.web.plus.doc.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocPropertiesTest {

    @Test
    void defaultContactUsesGenericProjectIdentity() {
        DocProperties properties = new DocProperties(null, null, null, null, null, null, null);

        assertEquals("AI Plus", properties.contact().name());
        assertEquals("", properties.contact().email());
        assertEquals("https://github.com/guanxiangkai/ai-plus", properties.contact().url());
    }
}
