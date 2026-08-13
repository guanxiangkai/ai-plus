package io.github.guanxiangkai.web.plus.job.enums;

import io.github.guanxiangkai.web.plus.core.enums.BaseEnum;
import io.github.guanxiangkai.web.plus.core.model.OptionItem;

import java.util.List;
import java.util.Map;

/**
 * 定时任务状态枚举
 * <p>
 * 描述一个调度任务的当前运行状态，对应 PowerJob 控制台「任务状态」字段。
 * 前端下拉框选项通过 {@link #getOptions()} 获取。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum JobStatus implements BaseEnum<Integer> {

    /**
     * 正常调度中，按配置的 Cron / 固定频率自动触发
     */
    NORMAL(0, "正常"),

    /**
     * 已暂停，不再自动触发，可手动执行
     */
    PAUSED(1, "暂停");

    /**
     * O(1) 反查缓存（GraalVM 编译时初始化）
     */
    private static final Map<Integer, JobStatus> CODE_MAP =
            BaseEnum.createCodeMap(JobStatus.class, JobStatus::getCode);

    private final Integer code;
    private final String description;

    JobStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据编码反查枚举（O(1)），未匹配时返回 {@link #NORMAL}。
     */
    public static JobStatus fromCode(Integer code) {
        return CODE_MAP.getOrDefault(code, NORMAL);
    }

    /**
     * 获取前端下拉框选项列表。
     */
    public static List<OptionItem> getOptions() {
        return BaseEnum.toOptions(JobStatus.values());
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDescription() {
        return description;
    }
}

