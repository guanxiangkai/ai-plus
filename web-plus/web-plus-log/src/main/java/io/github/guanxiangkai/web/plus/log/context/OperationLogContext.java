package io.github.guanxiangkai.web.plus.log.context;

/**
 * 操作日志上下文持有者（ThreadLocal + Reactor Context 双通道）
 *
 * <h3>核心用途</h3>
 * <p>
 * {@code @OperationLog} 切面在方法执行期间，通过此类将当前操作的元信息
 * （operationId、traceId、用户/租户信息、模块、类型编码/展示名）存入线程本地变量，
 * 使得同一请求链路上的 <b>jpa-plus 审计事件监听器</b>
 * 可以通过 {@link #current()} 获取上下文，从而将数据变更记录
 * 与操作日志记录精确关联（共享同一 {@code operationId}）。
 * </p>
 *
 * <h3>适用场景</h3>
 * <ul>
 *   <li><b>虚拟线程（推荐）</b>：{@code InheritableThreadLocal} 天然跨虚拟线程继承，
 *       子线程自动获得父线程上下文，零配置可用。</li>
 *   <li><b>WebFlux + 阻塞 JPA</b>：JPA 操作运行在 {@code Schedulers.boundedElastic()} 线程上，
 *       需通过 Micrometer Context Propagation 将 Reactor Context 桥接到线程本地变量。
 *       在 Spring Boot 4.x 中只需注册 {@link OperationLogContextAccessor}
 *       到 {@code ContextRegistry} 即可（见下方扩展示例）。</li>
 *   <li><b>同步/Spring MVC</b>：直接可用，无需额外配置。</li>
 * </ul>
 *
 * <h3>jpa-plus 数据审计关联（推荐扩展方式）</h3>
 * <pre>{@code
 * // ① 业务项目实现 OperationLogHandler（持久化操作日志）
 * @Component
 * public class SysOperLogHandler implements OperationLogHandler {
 *     @Override
 *     public void handle(OperationLogRecord record) {
 *         SysOperLog entity = convert(record);
 *         operLogRepository.save(entity);   // 使用 jpa-plus 持久化
 *     }
 * }
 *
 * // ② 框架内置 jpa-plus 审计桥接（或业务项目自定义监听 DataAuditEvent）
 * @Component
 * public class DataAuditEventListener {
 *
 *     @Autowired DataChangeHandler dataChangeHandler;
 *
 *     @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *     public void onDataAudit(DataAuditEvent event) {
 *         OperationContext ctx = OperationLogContext.current();
 *         if (ctx == null) return;  // 非操作日志发起的变更，忽略
 *
 *         BaseLog entity = ...; // 例如由桥接器按 web-plus.log.data-change-entity-class 创建
 *         LogEntityBinder.bindCommon(entity, ctx.traceId(), ctx.userId(), null, ctx.tenantId(),
 *             null, "SUCCESS", null);
 *         LogEntityBinder.set(entity, "operationId", ctx.operationId());
 *         LogEntityBinder.set(entity, "entityType", event.entity().getClass().getSimpleName());
 *         LogEntityBinder.set(entity, "entityId", resolveEntityId(event.entity()));
 *         LogEntityBinder.set(entity, "fieldChanges", toJson(mapFieldChanges(event)));
 *
 *         dataChangeHandler.handle(entity);
 *     }
 * }
 *
 * // ③（WebFlux + 阻塞 JPA 专用）注册 Micrometer 桥接器
 * @Bean
 * public OperationLogContextAccessor operationLogContextAccessor() {
 *     ContextRegistry.getInstance().registerThreadLocalAccessor(
 *         new OperationLogContextAccessor());
 *     return new OperationLogContextAccessor();
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.web.plus.log.spi.OperationLogHandler
 * @see io.github.guanxiangkai.web.plus.log.spi.DataChangeHandler
 * @since 1.0.0
 */
public final class OperationLogContext {

    /**
     * Reactor Context 中存储当前操作上下文的 Key。
     * <p>
     * 切面通过 {@code contextWrite(ctx -> ctx.put(REACTOR_KEY, operationContext))} 注入，
     * 可在下游 {@code Mono.deferContextual(view -> ...)} 中安全读取。
     * </p>
     */
    public static final String REACTOR_KEY = "web-plus.operation-log-context";

    /**
     * InheritableThreadLocal：子线程（虚拟线程/弹性调度线程）自动继承父线程上下文
     */
    private static final ThreadLocal<OperationContext> HOLDER = new InheritableThreadLocal<>();

    private OperationLogContext() {
    }

    // ────────────────────── ThreadLocal 操作 ──────────────────────

    /**
     * 设置当前线程的操作上下文（由 {@code OperationLogAspect} 调用）
     */
    public static void set(OperationContext ctx) {
        HOLDER.set(ctx);
    }

    /**
     * 读取当前线程的操作上下文。
     * <p>
     * 若返回 {@code null}，表示当前代码不在 {@code @OperationLog} 方法调用链路内，
     * 审计事件监听器可据此判断是否需要记录关联日志。
     * </p>
     *
     * @return 当前操作上下文，不在操作日志链路内时为 {@code null}
     */
    public static OperationContext current() {
        return HOLDER.get();
    }

    /**
     * 清除当前线程的操作上下文（由切面在 {@code doFinally} 中调用）
     */
    public static void clear() {
        HOLDER.remove();
    }

    // ────────────────────── 上下文数据模型 ──────────────────────

    /**
     * 当前操作的上下文快照（不可变）
     *
     * @param operationId   本次操作的全局唯一 ID（UUID），用于关联数据变更记录
     * @param traceId       链路追踪 ID（来自请求头或自动生成）
     * @param userId             当前操作用户 ID
     * @param tenantId           当前租户 ID
     * @param module             操作所属模块
     * @param description        操作描述（支持 SpEL 解析后的结果）
     * @param operationTypeCode  操作类型编码
     * @param operationTypeLabel 操作类型展示名
     */
    public record OperationContext(
            String operationId,
            String traceId,
            String userId,
            String tenantId,
            String module,
            String description,
            String operationTypeCode,
            String operationTypeLabel
    ) {
    }
}

