package io.github.guanxiangkai.jpa.plus.core.interceptor;

import io.github.guanxiangkai.jpa.plus.core.spi.Ordered;

import java.util.List;

/**
 * 拦截器链 SPI 贡献者接口
 *
 * <p>第三方 JAR 可将此接口实现注册为 Spring Bean，或通过 JDK 标准
 * {@code ServiceLoader} 声明实现类，即可向 JPA Plus 拦截器链中动态注入自定义拦截器。</p>
 *
 * <h3>使用方式</h3>
 * <p>1. 实现此接口：</p>
 * <pre>{@code
 * public class MyContributor implements InterceptorChainContributor {
 *     @Override
 *     public List<DataInterceptor> contribute() {
 *         return List.of(new MyCustomInterceptor());
 *     }
 * }
 * }</pre>
 *
 * <p>2. 优先将实现注册为 Spring Bean；非 Spring 场景可在 JAR 的
 * {@code META-INF/services/io.github.guanxiangkai.jpa.plus.core.interceptor.InterceptorChainContributor}
 * 文件中注册实现类全限定名（每行一个）：</p>
 * <pre>{@code
 * com.example.MyContributor
 * }</pre>
 *
 * <p><b>注意：</b>SPI 实现类必须提供无参构造方法；如需 Spring Bean 依赖，
 * 请将贡献者或 {@link DataInterceptor} 直接注册为 Spring Bean，框架会自动收集。</p>
 *
 * <p><b>热更新：</b>调用 {@code JpaPlusLoader.invalidate(InterceptorChainContributor.class)}
 * 清除缓存后，下次重建 {@link InterceptorChain} 会重新加载贡献者。</p>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.jpa.plus.core.spi.JpaPlusLoader
 * @since 1.0.0
 */
public interface InterceptorChainContributor extends Ordered {

    /**
     * 提供需要加入拦截器链的拦截器列表
     *
     * @return 拦截器列表（不可为 {@code null}，可为空列表）
     */
    List<DataInterceptor> contribute();

    /**
     * 贡献者排序值（越小越优先处理，默认 0）
     *
     * <p>贡献者内部的拦截器顺序仍由各 {@link DataInterceptor#order()} 控制。</p>
     */
    default int order() {
        return 0;
    }
}
