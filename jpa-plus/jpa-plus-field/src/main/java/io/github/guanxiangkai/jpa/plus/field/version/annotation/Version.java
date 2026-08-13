package io.github.guanxiangkai.jpa.plus.field.version.annotation;

import java.lang.annotation.*;

/**
 * 乐观锁版本注解
 *
 * <p>标注在版本字段上（{@link Integer} 或 {@link Long}），保存时自动递增版本号。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Version {
}

