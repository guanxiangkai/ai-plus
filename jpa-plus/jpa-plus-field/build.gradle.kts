/**
 * jpa-plus-field — 字段治理（合并模块）
 *
 * <p>聚合所有 FieldHandler 实现：
 * <ul>
 *   <li>ID 自动生成（@AutoId → IdFieldHandler）</li>
 *   <li>自动填充（@CreateTime/@UpdateTime/@CreateBy/@UpdateBy → AutoFillFieldHandler）</li>
 *   <li>乐观锁（@Version → VersionFieldHandler）</li>
 *   <li>字段加密（@Encrypt → EncryptFieldHandler）</li>
 *   <li>字段脱敏（@Desensitize → DesensitizeFieldHandler）</li>
 *   <li>字典回写（@Dict → DictFieldHandler）</li>
 *   <li>敏感词检测（@SensitiveWord → SensitiveWordHandler）</li>
 * </ul>
 * </p>
 * <p><b>依赖边界：</b>模块仅公开 core 字段治理契约；第三方增强库保持 compileOnly，由业务方按需放入 classpath。</p>
 */
dependencies {
    api(projects.jpaPlusCore)
    // 可选第三方增强（用户按需引入其中任意库，框架自动激活对应能力）
    // houbb-sensitive-word：高级 DFA 敏感词引擎（全角/半角/拼音绕过检测）
    // hutool-crypto：国密 SM4 / 增强 AES（@Encrypt(customAlgorithm = HutoolEncryptAlgorithm.SM4_ECB.class)）
    // hutool-core：DesensitizedUtil / IdUtil / ReflectUtil 等通用工具
    compileOnly(libs.bundles.field.optional)
}

