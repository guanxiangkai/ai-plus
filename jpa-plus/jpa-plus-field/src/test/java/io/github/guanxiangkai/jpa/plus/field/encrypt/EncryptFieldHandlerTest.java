package io.github.guanxiangkai.jpa.plus.field.encrypt;

import io.github.guanxiangkai.jpa.plus.core.exception.JpaPlusException;
import io.github.guanxiangkai.jpa.plus.core.field.BatchCapableFieldHandler;
import io.github.guanxiangkai.jpa.plus.field.encrypt.annotation.Encrypt;
import io.github.guanxiangkai.jpa.plus.field.encrypt.enums.EncryptAlgorithm;
import io.github.guanxiangkai.jpa.plus.field.encrypt.handler.EncryptFieldHandler;
import io.github.guanxiangkai.jpa.plus.field.encrypt.spi.EncryptKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EncryptFieldHandler} 单元测试
 *
 * <p>覆盖：AES 加解密往返、CBC 往返、多版本密钥轮换、批量处理、
 * BatchCapableFieldHandler 标记接口声明。</p>
 */
class EncryptFieldHandlerTest {

    static final String KEY_16 = "TestKey16Bytes!!";
    static final String KEY_V2 = "NewKey16Bytes!!!";

    EncryptKeyProvider singleKeyProvider = new EncryptKeyProvider() {
        @Override
        public String getActiveVersion() {
            return "v1";
        }

        @Override
        public String getKeyByVersion(String v) {
            return KEY_16;
        }
    };

    EncryptFieldHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EncryptFieldHandler(singleKeyProvider);
    }

    // ─── 标记接口声明 ────────────────────────────────────────────────────

    @Test
    void implementsBatchCapableFieldHandler() {
        assertThat(handler).isInstanceOf(BatchCapableFieldHandler.class);
    }

    // ─── AES 单实体加解密往返 ────────────────────────────────────────────

    @Test
    void aes_encryptThenDecrypt_roundTrip() throws Exception {
        var entity = new AesEntity();
        entity.secret = "hello-world";
        Field f = AesEntity.class.getDeclaredField("secret");

        handler.beforeSave(entity, f);
        assertThat(entity.secret)
                .isNotEqualTo("hello-world")
                .startsWith("v1:");  // versioned prefix

        handler.afterQuery(entity, f);
        assertThat(entity.secret).isEqualTo("hello-world");
    }

    @Test
    void aes_nullValue_notEncrypted() throws Exception {
        var entity = new AesEntity();
        entity.secret = null;
        Field f = AesEntity.class.getDeclaredField("secret");

        handler.beforeSave(entity, f);
        assertThat(entity.secret).isNull();
    }

    @Test
    void aes_unversionedCipherText_rejected() throws Exception {
        var entity = new AesEntity();
        entity.secret = "plain-cipher-without-version";
        Field f = AesEntity.class.getDeclaredField("secret");

        assertThatThrownBy(() -> handler.afterQuery(entity, f))
                .isInstanceOf(JpaPlusException.class)
                .hasMessageContaining("字段解密失败")
                .rootCause()
                .hasMessageContaining("缺少版本前缀");
    }

    // ─── AES-CBC 单实体加解密往返 ────────────────────────────────────────

    @Test
    void aesCbc_encryptThenDecrypt_roundTrip() throws Exception {
        var entity = new AesCbcEntity();
        entity.card = "6225 1234 5678 9012";
        Field f = AesCbcEntity.class.getDeclaredField("card");

        handler.beforeSave(entity, f);
        assertThat(entity.card).isNotEqualTo("6225 1234 5678 9012");

        handler.afterQuery(entity, f);
        assertThat(entity.card).isEqualTo("6225 1234 5678 9012");
    }

    @Test
    void aesCbc_deterministic_sameInputSameCipherText() throws Exception {
        var e1 = new AesCbcEntity();
        var e2 = new AesCbcEntity();
        e1.card = "same-plaintext";
        e2.card = "same-plaintext";
        Field f = AesCbcEntity.class.getDeclaredField("card");

        handler.beforeSave(e1, f);
        handler.beforeSave(e2, f);
        assertThat(e1.card).isEqualTo(e2.card);
    }

    // ─── 批量处理 ────────────────────────────────────────────────────────

    @Test
    void batch_encryptAndDecrypt_allEntities() throws Exception {
        List<AesEntity> entities = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            var e = new AesEntity();
            e.secret = "secret-" + i;
            entities.add(e);
        }
        Field f = AesEntity.class.getDeclaredField("secret");

        handler.beforeSaveBatch(entities, f);
        entities.forEach(e -> assertThat(e.secret).startsWith("v1:"));

        handler.afterQueryBatch(entities, f);
        for (int i = 0; i < 5; i++) {
            assertThat(entities.get(i).secret).isEqualTo("secret-" + i);
        }
    }

    @Test
    void batch_emptyList_noException() throws Exception {
        Field f = AesEntity.class.getDeclaredField("secret");
        handler.beforeSaveBatch(List.of(), f);  // should not throw
        handler.afterQueryBatch(List.of(), f);  // should not throw
    }

    @Test
    void batch_reusesAlgorithmAndKey_notRecalculatedPerEntity() throws Exception {
        // Performance smoke test: 1000 entities should complete quickly
        List<AesEntity> entities = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            var e = new AesEntity();
            e.secret = "value-" + i;
            entities.add(e);
        }
        Field f = AesEntity.class.getDeclaredField("secret");

        long start = System.currentTimeMillis();
        handler.beforeSaveBatch(entities, f);
        handler.afterQueryBatch(entities, f);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(5000); // generous bound for CI
        for (int i = 0; i < 1000; i++) {
            assertThat(entities.get(i).secret).isEqualTo("value-" + i);
        }
    }

    // ─── 密钥轮换 ────────────────────────────────────────────────────────

    @Test
    void keyRotation_oldCipherTextDecryptedWithOldKey() throws Exception {
        // Encrypt with v1 key
        var entity = new AesEntity();
        entity.secret = "rotate-me";
        Field f = AesEntity.class.getDeclaredField("secret");
        handler.beforeSave(entity, f);
        String v1Cipher = entity.secret;

        // Provider now returns v2 as active, but can still decrypt v1
        EncryptKeyProvider rotatedProvider = new EncryptKeyProvider() {
            @Override
            public String getActiveVersion() {
                return "v2";
            }

            @Override
            public String getKeyByVersion(String v) {
                return "v2".equals(v) ? KEY_V2 : KEY_16;
            }
        };
        EncryptFieldHandler rotatedHandler = new EncryptFieldHandler(rotatedProvider);

        // v1-encrypted cipher should still decrypt correctly
        entity.secret = v1Cipher;
        rotatedHandler.afterQuery(entity, f);
        assertThat(entity.secret).isEqualTo("rotate-me");
    }

    // ─── 测试实体 ─────────────────────────────────────────────────────────

    static class AesEntity {
        @Encrypt
        String secret;
    }

    static class AesCbcEntity {
        @Encrypt(algorithm = EncryptAlgorithm.AES_CBC)
        String card;
    }
}
