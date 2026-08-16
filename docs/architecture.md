# 架构说明

## 决策

仓库采用单 Git monorepo 与三个 Gradle included build。每个能力族拥有自己的版本目录、依赖目录和模块构建脚本；根构建只负责统一任务、跨构建依赖替换和发布编排。

```text
ai-plus
├── jpa-plus       8 个独立发布模块
├── redis-plus    19 个独立发布模块
└── web-plus      12 个独立发布模块
       ├── 依赖 JPA Plus 公开模块
       └── 依赖 Redis Plus 公开模块
```

## 依赖方向

- JPA Plus 与 Redis Plus 互不依赖。
- Web Plus 只能依赖 JPA Plus、Redis Plus 的公开 Maven 模块。
- 同一能力族内部使用 Gradle project dependency；发布 POM 使用被依赖模块的独立版本坐标。
- 根 composite build 将跨能力族 Maven 坐标替换为本地 included build，保证同一次 CI 可以验证完整依赖图。

## 版本规则

`gradle/module-versions.properties` 是全部模块版本的唯一权威来源。每个键必须与一个可发布模块同名。

## 发布边界

制品仅发布到 GitHub Packages。手工发布接收明确模块清单，`v*` 标签发布全部模块；标签必须指向 `main`。工作流执行完整构建后才发布，并使用仓库临时 Token，不保存长期发布凭据，也不生成 Maven Central 所需的 PGP 私钥。
