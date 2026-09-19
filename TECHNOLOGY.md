# 技术基线与采用依据

核验日期：2026-09-18。范围为 Gradle Wrapper、Version Catalog 中显式版本及业务制品保护的直接执行路径。
“稳定版本已存在”来自官方版本元数据、Maven Central 或 Plugin Portal；它不代表当前分支已通过兼容性验证。
Spring BOM 管理的全部传递依赖、安全公告和全部 GitHub Actions 固定提交不在此次逐项核验范围内。

## 版本决策

| 组件 | 当前目录版本 | 核验与采用边界 |
| --- | --- | --- |
| Gradle | 9.7.1 | 官方 current 稳定版；分发 SHA-256 与 Wrapper 配置一致 |
| Spring Boot / Cloud | 4.1.1 / 2025.1.3 | 当前稳定版；不选择 Boot 4.2.0-M1 |
| ProGuard | 7.10.0 | 当前稳定版，构建工具通过独立 JVM 调用 |
| Maven Plugin API / Core | 3.9.16 | Maven 官方建议 3.9.x 用户使用该稳定补丁；不选择 4.0.0 RC |
| SLF4J | 2.0.19 | JPA 模块只依赖标准 Logger/LoggerFactory 门面；需 CI 确认解析与测试 |
| Versions Plugin | 0.64.0 | 最新稳定版本，仅影响依赖更新报告任务 |
| Lombok / FreeFair | 1.18.48 / 9.5.0 | 元数据中的当前稳定版；生成代码兼容性仍由 Linux CI 验证 |
| JUnit / Testcontainers / ArchUnit | 6.1.3 / 2.0.5 / 1.5.0 | 元数据中的当前稳定版 |
| Error Prone / NullAway / Error Prone Plugin | 2.50.0 / 0.14.1 / 5.1.1 | 元数据中的当前稳定版 |
| Vanniktech Publish | 0.37.0 | 元数据中的当前稳定版 |
| Redisson / Hutool / Druid | 4.7.0 / 5.8.47 / 1.2.28 | 元数据中的当前稳定版 |
| Sensitive Word / Datasource Proxy | 0.29.5 / 1.11.0 | 元数据中的当前稳定版 |
| MapStruct Plus / FastExcel / POI | 1.5.2 / 1.3.0 / 5.5.1 | 元数据中的当前稳定版 |
| PowerJob / JJWT | 5.1.2 / 0.13.0 | 元数据中的当前稳定版 |

以下组件的采用依据覆盖项目实际调用面；发布说明与静态源码核对不替代 Linux CI 的运行证据：

| 组件 | 当前锁定值 | 兼容性证据与必要验收 |
| --- | --- | --- |
| Bucket4j | 8.20.0 | 官方源码保留 `Bucket.builder`、`Bandwidth.builder`、`tryConsume`；CI 覆盖限流耗尽和 key 隔离 |
| Springdoc / Swagger Annotations | 3.1.1 / 2.2.55 | Springdoc 官方明确升级 swagger-core 到 2.2.55；annotations Java 源码无改变，`Duration`/间接 Set 文档输出有变化；CI 验证真实 `/v3/api-docs` |
| Bouncy Castle | 1.86 | 官方 SM4 provider 保留 CBC/128 位 KeyGen；SM4 变更涉及项目未使用的 KeyAgreement/CMS 和 GCM/CCM 参数；CI 验证 Hutool 实际 provider 与加解密 |
| Oracle GraalVM | 25.0.4 | Gradle 9.7.1 支持 JDK 25；当前 `setup-java` GraalVM resolver 与官网 25.3 发行线目录不匹配，保持基线 |

GraalVM 官网 SDKMAN 示例为 `25.3.4+1.r25-graal`，GDS archive 使用
`25i3/archive/graalvm-jdk-25i3-25.0.4.1_linux-x64_bin.tar.gz`。当前所核对的 `setup-java` resolver
将带点版本直接拼到 Oracle 的 `graalvm/25/archive/graalvm-jdk-{version}_linux-x64_bin.tar.gz`。
该位置的 25.0.4 HEAD 为 200，而 25.3.4、25.3.4.1 及 SDKMAN 标识为 404，不能仅修改 java-version。
需先为新发行线选择并验证受支持的安装方式及完整构建；现有 workflow 安装机制保持不变。

JPA 模块发布 SLF4J runtime 依赖，其版本会进入 POM。JPA 的内部依赖以及 Web 的公开、运行时和可选
依赖共同构成版本传播范围；版本表中的相关 JPA/Web 模块使用独立补丁版本。Redis 限流模块、对应 starter
与聚合 starter 的版本覆盖 Bucket4j 发布依赖闭包。Artifact Plus 与授权模块的
初始版本统一为 1.0.0；可用性以 Maven Central 实际坐标为准。Maven 插件编译使用 3.9.16 API，声明的最低
Maven 运行版本为 3.9.11；当前只调用稳定 Maven 3 接口，兼容性需要消费方 CI 验收。

## 业务制品保护的架构

核心编排只依赖 JDK，Gradle/Maven 插件适配各自生命周期。公开基础库继续正常发布，业务项目显式选择
保护范围。混淆、签名和验签不进入业务运行时依赖，也不需要在 Spring 启动阶段注入解密 ClassLoader。

- **保守成员混淆**：ProGuard 保留类名、公开契约和注解语义，不裁剪、不优化；反射私有成员由业务方
  显式配置保留规则。ProGuard 作为独立工具使用，其许可证义务仍适用。
- **受约束的外部配置**：额外文件只接受完整 keep 块，逐块检查边界，不允许 include、系统属性替换、
  选项修饰符或输出重定向。实际读取有 1 MiB 上限，避免文件增长绕过预先大小检查。
- **一致的输入**：先创建输入快照并检查源文件状态，提取业务 class 和重打包都使用同一快照。
  这是构建一致性措施；输入、输出和临时目录仍须由当前任务独占，不能防御掌握构建账户权限的攻击者。
- **有界子进程**：外部 JVM 有超时及回收等待上限；输出持续排空，只保留首尾共 32 KiB 诊断。
  正常退出后先等待读取完毕，避免丢失尾部错误或把主动关闭管道误判成工具失败。
- **标准签名与外部信任**：RSA-PSS 签名覆盖最终 JAR 全部字节；部署端以独立管理的公钥和工具验签。
  签名确认完整性与持钥者身份，不能隐藏源码，也不会自动拦截 `java -jar`。
- **按需配置构建任务**：Gradle 使用 Provider、TaskProvider 和 `tasks.named`；混淆工具依赖按开关解析。
  出站变体在项目配置结束时确定是否可消费，关闭状态不提供伪保护制品。

暂不添加字节码加密、自定义 ClassLoader 或 Native Image。前两者会影响 Boot/JPA/反射等
加载路径，也无法阻止拥有运行环境控制权的人获得运行时明文；Native Image 则需要独立处理反射元数据
及平台制品。它们不应作为“防破解 JAR”的默认开关。

软件授权由独立 `web-plus-license` 内核与显式选用的 `web-plus-license-starter` 实现，支持签名离线
许可证及在线短期租约；不依赖构建工具、不复用登录 Token 信任域。在线每次续租绑定随机 nonce，
租约最长 15 分钟，失败不延期、不转离线；离线授权不能即时撤销，也不能抵抗整机时钟/快照操纵。
入口、配置、签发 API 和服务端协议见 [授权接入说明](web-plus/web-plus-license-starter/README.md)。
本仓库未部署授权服务；若需阻止掌握客户运行机器的人盗用核心能力，关键逻辑必须留在发行方控制的
服务端并逐次授权。本地校验、实例字符串或密钥下发本身均不能提供“绝对不可破解”的保证。

GitHub Artifact Attestations 的来源证明机制值得作为发行链路补充：它能把摘要与构建身份、工作流关联。
其可信身份和验收策略与离线 RSA 公钥不同，且需要额外的工作流写权限，本分支不启用该外部服务。
GitHub 2024 年的介绍博客仅作为机制来源，不用于推断当前产品资格、收费或最新 Action 版本。

## 验证边界

本机仅完成源码修改、差异空白检查及 Maven 描述符 XML 静态检查；没有执行 Gradle、Maven、JUnit、
TestKit 或仓库 Python 验证脚本，不能宣称已编译或测试通过。

Linux CI 中的验收入口是 `buildAll generatePomAll`、`.github/scripts/verify-build.py` 和
`.github/scripts/verify-artifact-maven.py`。测试源码覆盖真实 ProGuard、快照一致性、规则注入拒绝、
子进程输出/超时/中断、Gradle 配置缓存、Maven 描述符与实际 Boot 消费者、OpenSSL 互通及篡改拒绝。
授权测试覆盖 PS256/上下文/nonce/时间、启动拒绝、续租失败与拒绝、当前进程的时钟回拨及请求入口；
依赖回归覆盖 Bucket4j、SM4 provider/加解密和真实 OpenAPI JSON。所有新增与受影响套件均由 CI 报告门禁检查。
这些代码的存在不是执行证据；发布前还需使用保护后的业务 JAR 做实际业务验收。

## 来源

- [Gradle 官方当前版本](https://services.gradle.org/versions/current)
- [Maven 3.9.16 官方发行说明](https://maven.apache.org/docs/3.9.16/release-notes.html)
- [GraalVM 官方下载](https://www.graalvm.org/downloads/)：区分 JDK 基线与 GraalVM 发行线。
- [Gradle 9.7.1 Java 兼容表](https://docs.gradle.org/9.7.1/userguide/compatibility.html)
- [工作流固定提交的 setup-java GraalVM resolver](https://github.com/actions/setup-java/blob/dd06d9cba3e5552c54d9f8ea23572deb30010f7c/src/distributions/graalvm/installer.ts#L234)
- [Bucket4j 8.20.0 发布说明](https://github.com/bucket4j/bucket4j/releases/tag/8.20.0)
- [Springdoc 3.1.1 发布说明](https://github.com/springdoc/springdoc-openapi/releases/tag/v3.1.1)
- [Swagger 2.2.55 发布说明](https://github.com/swagger-api/swagger-core/releases/tag/v2.2.55)
- [Bouncy Castle 1.86 官方发布说明](https://github.com/bcgit/bc-java/blob/r1rv86/docs/releasenotes.md)
- [Maven Central](https://repo.maven.apache.org/maven2/) 与 [Gradle Plugin Portal](https://plugins.gradle.org/m2/)：按 catalog 坐标读取 `maven-metadata.xml`，排除 M/RC/alpha/beta/SNAPSHOT。
- [ProGuard 配置手册](https://www.guardsquare.com/manual/configuration/usage)
- [Gradle 避免过早配置任务](https://docs.gradle.org/current/userguide/task_configuration_avoidance.html)
- [Gradle 插件发布约定](https://docs.gradle.org/current/userguide/java_gradle_plugin.html#sec:gradle_plugin_dev_maven_publish)
- [Gradle 论坛：afterEvaluate 与懒任务配置](https://discuss.gradle.org/t/gradle-5-0-rc1-and-afterevaluate/29401/4)：用于理解生命周期风险，不把旧讨论等同当前 API 保证。
- [GitHub 官方博客：Artifact Attestations](https://github.blog/news-insights/product-news/introducing-artifact-attestations-now-in-public-beta/)
- [Springdoc 官网](https://springdoc.org/)：页面标识 3.1.1，并描述 Scalar 转发头处理变化；本仓库不因该页面自动引入 Scalar/MCP。
