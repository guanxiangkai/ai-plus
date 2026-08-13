package io.github.guanxiangkai.web.plus.job.handler;

import lombok.extern.slf4j.Slf4j;
import tech.powerjob.worker.core.processor.ProcessResult;
import tech.powerjob.worker.core.processor.TaskContext;
import tech.powerjob.worker.core.processor.sdk.BasicProcessor;

/**
 * PowerJob 任务处理器抽象基类
 * <p>
 * 同时实现 {@link TaskHandler} 与 PowerJob 的 {@link BasicProcessor}，
 * 业务模块继承此类并注册为 Spring Bean 即可接入 PowerJob 分布式调度。
 * </p>
 *
 * <pre>
 * 使用示例（ai-module-system）：
 *
 * {@code
 * @Component
 * @ScheduledTask(name = "清理过期文件", description = "定期清理 OSS 过期文件记录")
 * public class CleanExpiredOssFilesHandler extends AbstractPowerJobHandler {
 *     @Override
 *     public void execute(String params) {
 *         // 具体业务逻辑
 *     }
 * }
 * }
 * </pre>
 *
 * <p>在 PowerJob 控制台创建任务时，处理器信息填写 Spring Bean 名称，
 * 例如 {@code cleanExpiredOssFilesHandler}。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractPowerJobHandler implements TaskHandler, BasicProcessor {

    /**
     * PowerJob Worker 框架回调入口。
     * <p>将 {@link TaskContext#getJobParams()} 提取后委托给 {@link #execute(String)}。</p>
     *
     * @param context PowerJob 任务上下文（包含任务参数、实例 ID 等）
     * @return 处理结果
     */
    @Override
    public ProcessResult process(TaskContext context) throws Exception {
        String params = context.getJobParams();
        log.info("[PowerJob] 开始执行任务: handler={}, instanceId={}, params={}",
                getName(), context.getInstanceId(), params);
        try {
            execute(params);
            log.info("[PowerJob] 任务执行成功: handler={}, instanceId={}",
                    getName(), context.getInstanceId());
            return new ProcessResult(true, "SUCCESS");
        } catch (Exception e) {
            log.error("[PowerJob] 任务执行失败: handler={}, instanceId={}",
                    getName(), context.getInstanceId(), e);
            return new ProcessResult(false, e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        }
    }

    /**
     * 任务处理器名称，默认取类简名。
     * <p>需与 PowerJob 控制台注册的处理器名称一致。</p>
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}

