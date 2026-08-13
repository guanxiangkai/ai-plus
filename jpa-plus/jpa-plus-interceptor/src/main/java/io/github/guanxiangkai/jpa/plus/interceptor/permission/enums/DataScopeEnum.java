package io.github.guanxiangkai.jpa.plus.interceptor.permission.enums;

/**
 * 数据权限范围接口（可扩展枚举模式）
 *
 * <p>内置实现见 {@link DataScopeType}。
 * 用户可自定义枚举实现此接口，支持任意权限粒度。</p>
 *
 * <h3>扩展示例</h3>
 * <pre>{@code
 * public enum MyDataScope implements DataScope {
 *     // 按区域过滤
 *     REGION {
 *         @Override public String scopeName() { return "REGION"; }
 *     },
 *     // 按角色动态决定
 *     ROLE_BASED {
 *         @Override public String scopeName() { return "ROLE_BASED"; }
 *     };
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface DataScopeEnum {

    /**
     * 权限范围名称
     */
    String scopeName();

    /**
     * 是否跳过权限过滤（如 ALL 类型）
     */
    default boolean skipFilter() {
        return false;
    }
}

