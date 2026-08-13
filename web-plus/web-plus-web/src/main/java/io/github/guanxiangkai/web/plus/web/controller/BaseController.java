package io.github.guanxiangkai.web.plus.web.controller;

import io.github.guanxiangkai.web.plus.core.entity.BaseEntity;
import io.github.guanxiangkai.web.plus.core.exception.CoreBizException;
import io.github.guanxiangkai.web.plus.core.model.ApiResponse;
import io.github.guanxiangkai.web.plus.core.model.PageQuery;
import io.github.guanxiangkai.web.plus.core.model.PageResponse;
import io.github.guanxiangkai.web.plus.log.annotation.OperationLog;
import io.github.guanxiangkai.web.plus.security.annotation.RequiresPermission;
import io.github.guanxiangkai.web.plus.web.annotation.ApiCrypto;
import io.github.guanxiangkai.web.plus.web.exception.ImportValidationException;
import io.github.guanxiangkai.web.plus.web.properties.ImportProperties;
import io.github.guanxiangkai.web.plus.web.service.IBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Set;

/**
 * 基础 CRUD 控制器
 * <p>
 * 在 {@link ReadOnlyBaseController} 的统一查询端点基础上提供写入和导入接口。所有 JSON
 * 端点均叠加操作日志、权限校验和 API 加密注解，SpEL 表达式在运行时动态解析为具体子类实例的返回值：
 * </p>
 * <ul>
 *   <li>{@code #{getModuleName()}}       → 模块名，从包名 {@code *.controller.<module>.*} 中提取</li>
 *   <li>{@code #{getPermissionPrefix()}} → 权限前缀，如 {@code "sys:user"}</li>
 *   <li>{@code #{getEntityName()}}       → 实体中文名，从子类上的 {@code @Tag(name)} 推断</li>
 * </ul>
 *
 * <h3>子类约定</h3>
 * <ol>
 *   <li>继承本类</li>
 *   <li>实现 {@link #getService()}</li>
 *   <li>在子类上标注 {@code @RestController}、{@code @RequestMapping} 与
 *       {@code @Tag(name = "用户管理", description = "...")}（{@code name} 驱动实体名与模块名推断）</li>
 *   <li>（可选）覆盖 {@code getEntityName()} / {@code getModuleName()} / {@code getPermissionPrefix()}
 *       以完全自定义，框架已提供合理默认值</li>
 * </ol>
 *
 * <h3>权限码约定</h3>
 * <pre>
 * 前缀          + 后缀     → 完整权限码
 * sys:user      :list      → sys:user:list    （分页列表查询）
 * sys:user      :query     → sys:user:query   （详情查询）
 * sys:user      :add       → sys:user:add     （新增）
 * sys:user      :edit      → sys:user:edit    （修改 / 启用状态）
 * sys:user      :delete    → sys:user:delete  （单条 / 批量删除）
 * </pre>
 *
 * <h3>返回值约定</h3>
 * <p>所有阻塞式 Service 调用均通过继承的 {@code executeBlocking()} 在有界弹性调度器中惰性执行，确保：</p>
 * <ul>
 *   <li>{@link io.github.guanxiangkai.web.plus.log.aspect.OperationLogAspect} 的 Reactor Context 传播路径生效</li>
 *   <li>与 Spring Boot 4 虚拟线程调度器协作，阻塞调用不占用 Netty 事件循环线程</li>
 * </ul>
 *
 * @param <Q>  分页查询 DTO
 * @param <LV> 列表 VO
 * @param <DV> 详情 VO
 * @param <C>  创建 DTO
 * @param <U>  更新 DTO
 * @param <E>  实体类型
 * @author guanxiangkai
 * @since 1.0.0
 */
public abstract class BaseController<Q extends PageQuery, LV, DV, C, U, E extends BaseEntity>
        extends ReadOnlyBaseController<Q, LV, DV> {

    private ImportProperties importProperties = new ImportProperties();

    @Autowired
    void setImportProperties(ImportProperties importProperties) {
        this.importProperties = importProperties;
    }

    @Override
    protected abstract IBaseService<Q, LV, DV, C, U, E> getService();

    // ────────────────────────── 写入接口 ──────────────────────────

    @RequiresPermission("#{getPermissionPrefix() + ':add'}")
    @OperationLog(typeCode = "INSERT", module = "#{getModuleName()}",
            description = "#{getEntityName() + '新增'}")
    @Operation(summary = "新增", description = "新增数据")
    @ApiCrypto
    @PostMapping
    public Mono<ApiResponse<String>> create(@Valid @RequestBody C dto) {
        return executeBlocking(() -> ApiResponse.ok(getService().create(dto)));
    }

    @RequiresPermission("#{getPermissionPrefix() + ':edit'}")
    @OperationLog(typeCode = "UPDATE", module = "#{getModuleName()}",
            description = "#{getEntityName() + '修改'}")
    @Operation(summary = "修改", description = "根据ID修改数据")
    @ApiCrypto
    @PutMapping("/{id}")
    public Mono<ApiResponse<DV>> update(
            @Parameter(description = "数据ID", required = true) @PathVariable String id,
            @RequestBody U dto) {
        return executeBlocking(() -> {
            getService().update(id, dto);
            return ApiResponse.ok(getService().detail(id));
        });
    }

    @RequiresPermission("#{getPermissionPrefix() + ':edit'}")
    @OperationLog(typeCode = "UPDATE", module = "#{getModuleName()}",
            description = "#{getEntityName() + '启用状态更新'}")
    @Operation(summary = "更新启用状态", description = "根据ID更新数据的启用/禁用状态")
    @ApiCrypto
    @PutMapping("/{id}/enabled")
    public Mono<ApiResponse<DV>> updateEnabled(
            @Parameter(description = "数据ID", required = true) @PathVariable String id,
            @Parameter(description = "启用状态：true=启用，false=禁用", required = true) @RequestParam Boolean enabled) {
        return executeBlocking(() -> {
            getService().updateEnabled(id, enabled);
            return ApiResponse.ok(getService().detail(id));
        });
    }

    // ────────────────────────── 删除接口 ──────────────────────────

    @RequiresPermission("#{getPermissionPrefix() + ':edit'}")
    @OperationLog(typeCode = "UPDATE", module = "#{getModuleName()}",
            description = "#{getEntityName() + '批量启用状态更新'}")
    @Operation(summary = "批量更新启用状态", description = "根据ID列表批量更新数据的启用/禁用状态")
    @ApiCrypto
    @PutMapping("/batch/enabled")
    public Mono<ApiResponse<Void>> batchUpdateEnabled(
            @Parameter(description = "数据ID列表", required = true) @RequestBody List<String> ids,
            @Parameter(description = "启用状态：true=启用，false=禁用", required = true) @RequestParam Boolean enabled) {
        return executeBlocking(() -> {
            getService().batchUpdateEnabled(ids, enabled);
            return ApiResponse.ok();
        });
    }

    @RequiresPermission("#{getPermissionPrefix() + ':delete'}")
    @OperationLog(typeCode = "DELETE", module = "#{getModuleName()}",
            description = "#{getEntityName() + '删除'}")
    @Operation(summary = "删除", description = "根据ID软删除数据")
    @ApiCrypto
    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(
            @Parameter(description = "数据ID", required = true) @PathVariable String id) {
        return executeBlocking(() -> {
            getService().delete(id);
            return ApiResponse.ok();
        });
    }

    // ────────────────────────── 导入接口 ──────────────────────────

    @RequiresPermission("#{getPermissionPrefix() + ':delete'}")
    @OperationLog(typeCode = "DELETE", module = "#{getModuleName()}",
            description = "#{getEntityName() + '批量删除'}")
    @Operation(summary = "批量删除", description = "根据ID列表批量软删除数据")
    @ApiCrypto
    @DeleteMapping("/batch")
    public Mono<ApiResponse<Void>> batchDelete(
            @Parameter(description = "数据ID列表", required = true) @RequestBody List<String> ids) {
        return executeBlocking(() -> {
            getService().batchDelete(ids);
            return ApiResponse.ok();
        });
    }

    @RequiresPermission("#{getPermissionPrefix() + ':import'}")
    @OperationLog(typeCode = "IMPORT", module = "#{getModuleName()}",
            description = "#{getEntityName() + '导入'}")
    @Operation(summary = "导入", description = "导入数据，仅支持 Excel（.xlsx / .xls）和 Word（.doc / .docx）格式")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApiResponse<?>> importData(
            @Parameter(description = "导入文件（Excel 或 Word）", required = true)
            @RequestPart("file") Mono<FilePart> filePart) {
        return filePart.flatMap(part -> {
            String filename = part.filename() == null ? "" : part.filename();
            String lower = filename.toLowerCase();
            if (getImportExtensions().stream().noneMatch(lower::endsWith)) {
                return Mono.<ApiResponse<?>>just(ApiResponse.fail(
                        ApiResponse.BUSINESS_ERROR_CODE,
                        "导入失败：仅支持 Excel（.xlsx/.xls）和 Word（.doc/.docx）格式"));
            }
            long maxBytes = getImportMaxBytes();
            return DataBufferUtils.join(part.content(), toDataBufferLimit(maxBytes))
                    .flatMap(buf -> {
                        byte[] bytes;
                        try {
                            bytes = new byte[buf.readableByteCount()];
                            buf.read(bytes);
                        } finally {
                            DataBufferUtils.release(buf);
                        }
                        return Mono.<ApiResponse<?>>fromCallable(() -> ApiResponse.ok(handleImport(bytes, filename)))
                                .subscribeOn(Schedulers.boundedElastic());
                    })
                    .onErrorResume(DataBufferLimitException.class, e -> Mono.<ApiResponse<?>>just(ApiResponse.fail(
                            ApiResponse.BUSINESS_ERROR_CODE,
                            "导入失败：文件大小超出限制，最大支持 " + formatSize(maxBytes))))
                    .onErrorResume(ImportValidationException.class, e -> Mono.<ApiResponse<?>>just(ApiResponse.fail(
                            ApiResponse.BUSINESS_ERROR_CODE, e.getMessage(), e.getErrors())));
        });
    }

    // ────────────────────────── 导入钩子（子类按需覆盖）──────────────────────────

    /**
     * 允许导入的文件后缀，默认为 {@code web-plus.import.allowed-extensions}。
     * <p>子类可覆盖以追加或替换支持的格式，例如：</p>
     * <pre>{@code
     * @Override
     * protected Set<String> getImportExtensions() {
     *     Set<String> exts = new HashSet<>(super.getImportExtensions());
     *     exts.add(".csv");
     *     return Collections.unmodifiableSet(exts);
     * }
     * }</pre>
     */
    protected Set<String> getImportExtensions() {
        return importProperties.getAllowedExtensions();
    }

    /**
     * 导入文件最大字节数，默认值由 {@code web-plus.import.max-file-size} 控制。
     * <p>子类可覆盖以放宽或收紧导入上限。</p>
     */
    protected long getImportMaxBytes() {
        return importProperties.maxFileSizeBytes();
    }

    /**
     * 导入钩子：子类覆盖以实现具体解析逻辑。
     *
     * @param fileBytes 文件字节流
     * @param filename  原始文件名（可据后缀区分 Excel / Word）
     * @return 成功导入的记录数
     */
    protected int handleImport(byte[] fileBytes, String filename) {
        throw CoreBizException.unsupported(getEntityName() + " 暂不支持导入");
    }

    private int toDataBufferLimit(long maxBytes) {
        if (maxBytes <= 0 || maxBytes > Integer.MAX_VALUE) {
            throw new IllegalStateException("导入文件大小限制必须在 1 到 " + Integer.MAX_VALUE + " 之间");
        }
        return (int) maxBytes;
    }

    private String formatSize(long maxBytes) {
        long megabytes = maxBytes / (1024 * 1024);
        if (megabytes > 0 && maxBytes % (1024 * 1024) == 0) {
            return megabytes + "MB";
        }
        long kilobytes = maxBytes / 1024;
        if (kilobytes > 0 && maxBytes % 1024 == 0) {
            return kilobytes + "KB";
        }
        return maxBytes + " bytes";
    }
}
