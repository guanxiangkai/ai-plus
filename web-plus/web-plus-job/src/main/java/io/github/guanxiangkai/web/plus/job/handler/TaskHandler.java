package io.github.guanxiangkai.web.plus.job.handler;

/**
 * PowerJob 任务处理器接口
 * <p>
 * 所有业务模块的定时任务处理器均需实现此接口，
 * 通常通过继承 {@link AbstractPowerJobHandler} 来完成接入。
 * </p>
 *
 * <pre>
 * 使用示例：
 *
 * {@code
 * @Component
 * @ScheduledTask(name = "清理过期会话", description = "每日定时清理已超时的在线会话记录")
 * public class CleanExpiredSessionsHandler extends AbstractPowerJobHandler {
 *     @Override
 *     public void execute(String params) {
 *         // 具体业务逻辑
 *     }
 * }
 * }
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface TaskHandler {

    /**
     * 执行定时任务业务逻辑。
     * <p>
     * 框架保证此方法在独立线程（虚拟线程）中调用；
     * 抛出任何异常均会被 {@link AbstractPowerJobHandler} 捕获，并将任务实例标记为失败。
     * </p>
     *
     * @param params 任务参数（在 PowerJob 控制台创建任务时填写，可为空字符串）
     * @throws Exception 业务异常，由框架捕获并记录
     */
    void execute(String params) throws Exception;

    /**
     * 处理器名称。
     * <p>
     * 须与 PowerJob 控制台注册任务时填写的「处理器信息（Spring Bean 名称）」一致。
     * 默认实现返回类简名，如需自定义可在子类中覆盖。
     * </p>
     *
     * @return 处理器唯一标识名
     */
    String getName();
}

