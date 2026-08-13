package io.github.guanxiangkai.web.plus.web.properties;

import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Web Plus 通用导入接口的文件策略。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "web-plus.import")
public class ImportProperties {

    @NotEmpty
    private Set<String> allowedExtensions = new LinkedHashSet<>(WebPlusConstants.IMPORT_EXTENSIONS);

    @NotNull
    private DataSize maxFileSize = DataSize.ofMegabytes(20);

    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(Set<String> allowedExtensions) {
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            throw new IllegalArgumentException("导入文件扩展名不能为空");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String extension : allowedExtensions) {
            if (extension == null || extension.isBlank()) {
                throw new IllegalArgumentException("导入文件扩展名不能为空");
            }
            String value = extension.trim().toLowerCase(Locale.ROOT);
            normalized.add(value.startsWith(".") ? value : "." + value);
        }
        this.allowedExtensions = Set.copyOf(normalized);
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        if (maxFileSize == null) {
            throw new IllegalArgumentException("导入文件大小不能为空");
        }
        long bytes = maxFileSize.toBytes();
        if (bytes <= 0 || bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("导入文件大小必须在 1 字节到 " + Integer.MAX_VALUE + " 字节之间");
        }
        this.maxFileSize = maxFileSize;
    }

    /** 返回 WebFlux 缓冲区可安全使用的字节上限。 */
    public int maxFileSizeBytes() {
        return Math.toIntExact(maxFileSize.toBytes());
    }
}
