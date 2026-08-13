package io.github.guanxiangkai.jpa.plus.datasource.enums;

/**
 * 数据源名称接口（可扩展枚举模式）
 *
 * <p>提供内置常量 {@link #MASTER} / {@link #SLAVE}，
 * 用户可自定义 {@code enum} 实现此接口，支持任意数据源名称。</p>
 *
 * <h3>在注解中使用（通过字符串常量）</h3>
 * <pre>{@code
 * @DS(DsName.MASTER)
 * @DS(DsName.SLAVE)
 * }</pre>
 *
 * <h3>在代码中使用</h3>
 * <pre>{@code
 * JpaPlusContext.withDS("slave", () -> {
 *     return userRepo.findById(1L);
 * });
 * }</pre>
 *
 * <h3>扩展方式</h3>
 * <pre>{@code
 * public enum MyDsName implements DsName {
 *     ANALYTICS("analytics"),
 *     REPORTING("reporting");
 *
 *     public static final String ANALYTICS_DS = "analytics";
 *     public static final String REPORTING_DS = "reporting";
 *
 *     private final String value;
 *     MyDsName(String value) { this.value = value; }
 *     @Override public String value() { return value; }
 * }
 *
 * @DS(MyDsName.ANALYTICS_DS)
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface DsName {

    // ─── 字符串常量（供 @DS 注解使用，注解要求编译时常量） ───

    String MASTER = "master";
    String SLAVE = "slave";

    /**
     * 数据源名称
     */
    String value();
}
