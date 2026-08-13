package io.github.guanxiangkai.web.plus.job.config;

import io.github.guanxiangkai.web.plus.job.annotation.ScheduledTask;
import io.github.guanxiangkai.web.plus.job.handler.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import tech.powerjob.worker.PowerJobSpringWorker;

import java.util.Map;

/**
 * PowerJob Worker 模块自动配置
 * <p>
 * 仅在 {@code powerjob-worker-spring-boot-starter} 在 classpath 时生效。
 * 各业务服务（ai-module-system / ai-module-agent / ai-module-business 等）
 * 引入 {@code web-plus-job} 后即可自动激活；
 * 在各自的 Nacos 配置中补充 {@code powerjob.worker.*} 连接参数即可完成接入。
 * </p>
 *
 * <p><b>Nacos 配置示例（各服务 yaml 中声明）：</b></p>
 * <pre>
 * powerjob:
 *   worker:
 *     enabled: true
 *     app-name: ${spring.application.name}   # 须与 PowerJob Server 注册的应用名一致
 *     server-address: 127.0.0.1:7700         # PowerJob Server 地址
 *     protocol: HTTP
 *     store-strategy: DISK
 *     max-result-length: 4096
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(PowerJobSpringWorker.class)
public class JobAutoConfiguration {

    public JobAutoConfiguration() {
        log.info("[AI-Common-Job] PowerJob Worker 自动配置已激活");
    }

    /**
     * 容器刷新完成后，扫描并打印所有已注册的 {@link TaskHandler} 处理器，
     * 便于快速核查 PowerJob 控制台中应填写的处理器 Bean 名称。
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed(ContextRefreshedEvent event) {
        ApplicationContext ctx = event.getApplicationContext();
        // 避免父子容器重复打印
        if (ctx.getParent() != null) {
            return;
        }
        Map<String, TaskHandler> handlers = ctx.getBeansOfType(TaskHandler.class);
        if (handlers.isEmpty()) {
            log.debug("[AI-Common-Job] 当前服务未注册任何 TaskHandler 处理器");
            return;
        }
        log.info("[AI-Common-Job] 已注册 {} 个 TaskHandler 处理器：", handlers.size());
        handlers.forEach((beanName, handler) -> {
            ScheduledTask meta = handler.getClass().getAnnotation(ScheduledTask.class);
            if (meta != null) {
                log.info("[AI-Common-Job]   ● Bean={} | 任务名={} | 说明={}",
                        beanName, meta.name(), meta.description());
            } else {
                log.info("[AI-Common-Job]   ● Bean={} | handlerName={}", beanName, handler.getName());
            }
        });
    }
}

