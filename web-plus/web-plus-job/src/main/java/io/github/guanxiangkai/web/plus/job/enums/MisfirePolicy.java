package io.github.guanxiangkai.web.plus.job.enums;

import io.github.guanxiangkai.web.plus.core.enums.BaseEnum;
import io.github.guanxiangkai.web.plus.core.model.OptionItem;

import java.util.List;
import java.util.Map;

/**
 * 调度错过触发策略枚举（Misfire Policy）
 * <p>
 * 当任务因服务宕机、资源不足等原因错过预定触发时间时，
 * 该策略决定恢复后的补偿行为。
 * </p>
 *
 * <ul>
 *   <li>{@link #DO_NOTHING}       — 跳过本次，等待下次正常触发（默认，推荐生产使用）</li>
 *   <li>{@link #FIRE_ONCE_NOW}    — 立即补偿触发一次，然后恢复正常调度</li>
 *   <li>{@link #IGNORE_MISFIRES}  — 立即补偿所有错过的触发次数（慎用，可能引发任务堆积）</li>
 * </ul>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum MisfirePolicy implements BaseEnum<Integer> {

    /**
     * 不做补偿，直接跳过错过的触发；等待下次正常触发时间
     */
    DO_NOTHING(0, "跳过补偿"),

    /**
     * 立即补偿触发一次，之后按正常调度继续
     */
    FIRE_ONCE_NOW(1, "补偿一次"),

    /**
     * 立即补偿所有错过的触发次数（高频任务慎用）
     */
    IGNORE_MISFIRES(2, "全部补偿");

    /**
     * O(1) 反查缓存（GraalVM 编译时初始化）
     */
    private static final Map<Integer, MisfirePolicy> CODE_MAP =
            BaseEnum.createCodeMap(MisfirePolicy.class, MisfirePolicy::getCode);

    private final Integer code;
    private final String description;

    MisfirePolicy(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据编码反查枚举（O(1)），未匹配时返回 {@link #DO_NOTHING}。
     */
    public static MisfirePolicy fromCode(Integer code) {
        return CODE_MAP.getOrDefault(code, DO_NOTHING);
    }

    /**
     * 获取前端下拉框选项列表。
     */
    public static List<OptionItem> getOptions() {
        return BaseEnum.toOptions(MisfirePolicy.values());
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

