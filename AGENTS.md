# AI Plus 仓库工程约定

- 本仓库统一维护 JPA Plus、Redis Plus 和 Web Plus，三个能力族都必须持续演进，并共同构成 Java 项目的基础框架。
- 持续审查下游项目的重复基础代码；经证明与业务无关、可稳定复用的能力，应下沉到职责匹配的 JPA Plus、Redis Plus 或 Web Plus 能力族，并由业务项目通过公开契约使用。
- 通用能力下沉不得携带产品菜单、租户数据、业务表结构或特定产品语义；业务边界仍由各业务项目维护。
- 源码、注释、测试、文档和配置只描述当前有效契约，不出现版本演进叙述，不保留并行接口、别名、降级分支或已弃用 API。
- 设计模式只用于稳定变化点和公共扩展点；优先使用模板方法、策略、工厂、适配器、责任链、状态和观察者等合适模式，禁止为凑齐23种模式制造空抽象。
- 根构建采用 Gradle composite build，三个能力族保持独立构建边界，跨能力族依赖必须通过公开 Maven 坐标和组合构建替换解析。
- 所有发布坐标和 Java 包名统一使用 `io.github.guanxiangkai`。
- 每个可发布模块的版本唯一记录在 `gradle/module-versions.properties`；只递增发生变化的模块及受其公开契约影响的下游模块。
- Maven Central 是公共制品的权威仓库，GitHub Packages 保留为同仓受管发布目标；Central 发布使用官方 OSSRH Staging 兼容 API 与 PGP 签名。
- 发布必须通过受保护的 GitHub Actions 环境手工选择模块，先执行完整构建；用户名、令牌和 PGP 私钥只使用 Actions Secrets，不得覆盖已经发布的同版本制品。
- Java 基线为 Oracle GraalVM 25.0.4，Spring 版本遵循各能力族锁定的稳定版本；公开 API 和关键边界提供准确简洁的中文 Javadoc。
- 下游 Java 项目优先通过公开 Maven 坐标复用本仓库能力；业务仓库不得复制 Controller、Service、Repository、鉴权、日志、缓存、幂等、消息和数据访问等通用实现。
- 可复用常量、枚举、值对象、转换规则和扩展策略只保留一个权威定义；调用方必须依赖该定义，不散落魔法值或重复判断。
- 依赖、构建、测试与制品验证必须在满足项目版本基线的 Linux 环境执行。
- 禁止提交 Token、密码、私钥、用户级 Maven/Gradle 凭据、构建产物与运行日志。
