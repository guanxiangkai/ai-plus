package io.github.guanxiangkai.jpa.plus.field.sensitive.spi;

import java.util.Collection;

/**
 * 敏感词白名单提供者 SPI 接口
 *
 * <p>白名单中的词汇即使命中了敏感词库，也会被放行（不拒绝、不替换）。</p>
 *
 * <h3>典型使用场景</h3>
 * <ul>
 *   <li>企业名称中包含敏感词（如 "金盾安全科技"），需要整词放行</li>
 *   <li>地址中包含地名，该地名与敏感词重叠</li>
 *   <li>特殊行业术语虽包含敏感词，但在该业务场景下允许</li>
 * </ul>
 *
 * <h3>放行规则</h3>
 * <p>白名单采用<b>精确匹配</b>：检测引擎找到一个敏感词后，检查该词的原文是否在白名单中。
 * 若命中白名单则跳过（不替换/不拒绝），继续检测后续内容。</p>
 *
 * <h3>实现示例</h3>
 * <pre>{@code
 * @Component
 * public class MyWhitelistProvider implements SensitiveWordWhitelistProvider {
 *     @Override
 *     public Collection<String> loadWhitelist() {
 *         return List.of("金盾安全科技", "公安局备案号");
 *     }
 * }
 * }</pre>
 *
 * <p><b>设计模式：</b>SPI 服务发现模式 + 策略模式</p>
 *
 * @author guanxiangkai
 * @see SensitiveWordProvider
 * @see io.github.guanxiangkai.jpa.plus.field.sensitive.handler.SensitiveWordHandler
 * @since 1.0.0
 */
@FunctionalInterface
public interface SensitiveWordWhitelistProvider {

    /**
     * 加载白名单词库
     *
     * <p>框架在启动时和每次 {@link io.github.guanxiangkai.jpa.plus.field.sensitive.handler.SensitiveWordHandler#refresh()}
     * 被调用时，会重新调用此方法并重建引擎。</p>
     *
     * <p>返回 {@code null} 或空集合时，白名单为空（所有敏感词命中均不放行）。</p>
     *
     * @return 白名单词集合（允许为 {@code null} 或空）
     */
    Collection<String> loadWhitelist();
}

