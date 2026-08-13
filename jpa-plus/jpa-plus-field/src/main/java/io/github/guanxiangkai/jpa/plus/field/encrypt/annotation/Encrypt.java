package io.github.guanxiangkai.jpa.plus.field.encrypt.annotation;

import io.github.guanxiangkai.jpa.plus.field.encrypt.enums.EncryptAlgorithm;
import io.github.guanxiangkai.jpa.plus.field.encrypt.enums.EncryptionAlgorithm;

import java.lang.annotation.*;

/**
 * 字段加密注解
 *
 * <p>标注在实体字段上，保存前自动加密、查询后自动解密。
 * 仅支持 {@link String} 类型字段。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 使用内置算法（默认 AES_CBC）
 * @Encrypt
 * private String idCard;
 *
 * // 指定算法
 * @Encrypt(algorithm = EncryptAlgorithm.AES_CBC)
 * private String bankCard;
 *
 * // 自定义算法
 * @Encrypt(customAlgorithm = MyAlgorithm.SM4.class)
 * private String secret;
 * }</pre>
 *
 * @author guanxiangkai
 * @see EncryptionAlgorithm
 * @see EncryptAlgorithm
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Encrypt {

    /**
     * 内置加密算法（默认 AES_CBC）
     */
    EncryptAlgorithm algorithm() default EncryptAlgorithm.AES_CBC;

    /**
     * 自定义加密算法类（优先于 {@link #algorithm()}）
     *
     * <p>指定一个实现了 {@link EncryptionAlgorithm} 的枚举或类。
     * 设为默认值 {@code EncryptionAlgorithm.class} 表示不使用自定义算法。</p>
     */
    Class<? extends EncryptionAlgorithm> customAlgorithm() default EncryptionAlgorithm.class;
}
