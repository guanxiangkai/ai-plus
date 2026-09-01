package io.github.guanxiangkai.web.plus.web.controller;

import io.github.guanxiangkai.web.plus.core.model.ApiResponse;
import io.github.guanxiangkai.web.plus.core.model.PageQuery;
import io.github.guanxiangkai.web.plus.core.model.PageResponse;
import io.github.guanxiangkai.web.plus.log.annotation.OperationLog;
import io.github.guanxiangkai.web.plus.security.annotation.RequiresPermission;
import io.github.guanxiangkai.web.plus.web.service.IReadOnlyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Supplier;

/**
 * 只读查询控制器基类。
 * <p>
 * 统一提供 {@code /list} 与 {@code /{id}} 查询端点，以及与 CRUD 控制器一致的
 * 权限、操作日志和 {@code Mono<ApiResponse<T>>} 响应契约。接口载荷加密由业务控制器或方法
 * 显式声明；仅提供查询投影的业务控制器应继承本类，避免引入无意义的写入 DTO 或实体泛型。
 * </p>
 *
 * <p>子类须实现 {@link #getService()}。权限和操作日志的 SpEL 表达式可直接调用本类公开的
 * 元数据方法。</p>
 *
 * @param <Q>  分页查询 DTO
 * @param <LV> 列表 VO
 * @param <DV> 详情 VO
 * @author guanxiangkai
 * @since 1.0.0
 */
public abstract class ReadOnlyBaseController<Q extends PageQuery, LV, DV> {

    /**
     * 提供控制器查询数据的只读 Service。
     *
     * @return 只读查询 Service
     */
    protected abstract IReadOnlyService<Q, LV, DV> getService();

    /**
     * 实体中文名，用于操作日志描述。
     *
     * @return 日志使用的实体名称
     */
    public String getEntityName() {
        Tag tagAnnotation = this.getClass().getAnnotation(Tag.class);
        if (tagAnnotation != null && tagAnnotation.name() != null) {
            String name = tagAnnotation.name();
            return name.endsWith("管理") ? name.substring(0, name.length() - 2) : name;
        }
        return extractEntityNameFromClassName();
    }

    /**
     * 模块名称，用于操作日志的 {@code module} 字段。
     *
     * @return 包路径推断后首字母大写的模块名称
     */
    public String getModuleName() {
        String moduleName = extractModuleFromPackage();
        return moduleName.substring(0, 1).toUpperCase() + moduleName.substring(1);
    }

    /**
     * 权限前缀，用于拼装接口权限码。
     *
     * @return {@code <module>:<controllerName>} 格式的权限前缀
     */
    public String getPermissionPrefix() {
        return extractModuleFromPackage() + ":" + extractControllerName();
    }

    /**
     * 分页查询列表数据。
     *
     * @param query 分页、排序及业务筛选条件
     * @return 统一分页响应
     */
    @RequiresPermission("#{getPermissionPrefix() + ':list'}")
    @OperationLog(typeCode = "QUERY", module = "#{getModuleName()}",
            description = "#{getEntityName() + '分页列表查询'}", saveRequestParams = false)
    @Operation(summary = "分页列表", description = "分页查询列表数据")
    @GetMapping("/list")
    public Mono<ApiResponse<PageResponse<LV>>> list(Q query) {
        return executeBlocking(() -> ApiResponse.ok(getService().list(query)));
    }

    /**
     * 根据标识查询详情。
     *
     * @param id 数据标识
     * @return 统一详情响应
     */
    @RequiresPermission("#{getPermissionPrefix() + ':query'}")
    @OperationLog(typeCode = "QUERY", module = "#{getModuleName()}",
            description = "#{getEntityName() + '详情查询'}")
    @Operation(summary = "详情", description = "根据ID查询详细信息")
    @GetMapping("/{id}")
    public Mono<ApiResponse<DV>> detail(
            @Parameter(description = "数据ID", required = true) @PathVariable String id) {
        return executeBlocking(() -> ApiResponse.ok(getService().detail(id)));
    }

    /**
     * 在有界弹性调度器中执行阻塞任务，避免 JPA、外部投影等同步调用占用 WebFlux 事件循环。
     *
     * @param operation 同步执行的任务
     * @param <T> 任务结果类型
     * @return 异步任务结果
     */
    protected final <T> Mono<T> executeBlocking(Supplier<T> operation) {
        return Mono.fromSupplier(operation).subscribeOn(Schedulers.boundedElastic());
    }

    private String extractModuleFromPackage() {
        String[] parts = this.getClass().getPackage().getName().split("\\.");
        for (int i = parts.length - 1; i >= 0; i--) {
            if ("controller".equals(parts[i])) {
                if (i + 1 < parts.length) {
                    return parts[i + 1];
                }
                if (i - 1 >= 0) {
                    return parts[i - 1];
                }
            }
        }
        return parts[parts.length - 1];
    }

    private String extractControllerName() {
        String className = this.getClass().getSimpleName();
        if (className.endsWith("Controller")) {
            className = className.substring(0, className.length() - "Controller".length());
        }
        return className.substring(0, 1).toLowerCase() + className.substring(1);
    }

    private String extractEntityNameFromClassName() {
        String className = this.getClass().getSimpleName();
        return className.endsWith("Controller")
                ? className.substring(0, className.length() - "Controller".length())
                : className;
    }
}
