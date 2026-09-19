# Artifact Plus

Artifact Plus 在业务项目构建时提供可选的成员混淆与完整 JAR 签名。它不加密字节码、
不承担运行时许可证服务，也不替代部署端的信任校验。基础库可以公开源码；保护对象由业务项目的包前缀指定。
业务软件授权通过独立的 [License Starter](../web-plus/web-plus-license-starter/README.md) 按需接入，
支持在线和离线模式；授权运行时不依赖 Artifact Plus。

| 模块 | 使用位置 |
| --- | --- |
| `artifact-plus-core` | 无第三方运行时依赖的保护编排、RSA-PSS 签名和独立验签 CLI |
| `artifact-plus-gradle-plugin` | 业务项目的 Gradle 构建插件 |
| `artifact-plus-maven-plugin` | 业务项目的 Maven 构建插件 |

模块版本见根 `gradle/module-versions.properties`。构建工具及验签 CLI 使用 JDK 25。
这些模块的源码和配置不代表制品已经发布；业务接入时应使用实际发布且通过 CI 的版本。

## Gradle 接入

在业务项目的 `settings.gradle.kts` 中，将插件 ID 映射到 Maven Central 上的模块坐标：

```kotlin
pluginManagement {
    repositories { mavenCentral(); gradlePluginPortal() }
    resolutionStrategy.eachPlugin {
        if (requested.id.id == "io.github.guanxiangkai.artifact-protection") {
            useModule("io.github.guanxiangkai:artifact-plus-gradle-plugin:${requested.version}")
        }
    }
}
```

在业务项目的 `build.gradle.kts` 中：

```kotlin
plugins {
    java
    id("io.github.guanxiangkai.artifact-protection") version "1.0.0"
    // Spring Boot 项目继续使用其已锁定的 org.springframework.boot 插件版本。
}

repositories { mavenCentral() }

artifactProtection {
    enabled.set(providers.gradleProperty("artifact.protection.enabled").map(String::toBoolean).orElse(false))
    obfuscationEnabled.set(providers.gradleProperty("artifact.obfuscation.enabled").map(String::toBoolean).orElse(false))
    signingEnabled.set(providers.gradleProperty("artifact.signing.enabled").map(String::toBoolean).orElse(false))
    packages.set(listOf("com.example.business"))
    // 项目确有反射成员时，创建保留规则文件后再配置此项。
    // keepRules.set(layout.projectDirectory.file("artifact-keep.pro"))
}
```

发布构建显式打开所需能力：

```bash
./gradlew clean build \
  -Partifact.protection.enabled=true \
  -Partifact.obfuscation.enabled=true \
  -Partifact.signing.enabled=true
```

`protectArtifact` 自动加入 `assemble`，输入默认来自 `jar`，存在 Boot 插件时默认来自 `bootJar`。
显式设置 `inputJar` 可覆盖默认输入。输出为 `build/protected/<项目名>.jar` 和同名 `.jar.sig`，
映射表为 `build/protected/mapping.txt`。这些路径可通过 `outputJar`、`mappingFile` 配置。
保护任务禁用缓存和 up-to-date 跳过，每次重新处理；仅启用混淆时解析 ProGuard 工具依赖。

发布流水线必须取 `protectArtifact.outputJar` 和 `signatureFile`；常规 `jar`/`bootJar` 输出和普通
Java publication 保持原用途，不会自动替换为保护版。`protectedArtifactElements` 仅在启用后可消费，
使用独立 Usage `artifact-protected`，可通过指定 configuration 显式获取。

## Maven 接入

在业务项目 POM 的 `build/plugins` 中声明：

```xml
<plugin>
  <groupId>io.github.guanxiangkai</groupId>
  <artifactId>artifact-plus-maven-plugin</artifactId>
  <version>1.0.0</version>
  <executions>
    <execution><goals><goal>protect</goal></goals></execution>
  </executions>
  <configuration>
    <packages><package>com.example.business</package></packages>
  </configuration>
</plugin>
```

```bash
mvn clean verify \
  -Dartifact.protection.enabled=true \
  -Dartifact.obfuscation.enabled=true \
  -Dartifact.signing.enabled=true
```

Maven 需使用 3.9.11 或更新的兼容版本。`protect` 默认绑定 `verify`，在 `package` 和 Boot `repackage`
完成后执行；仅运行 `mvn package` 不会执行保护。输出位于 `target/protected/`，成功后附加
classifier `protected` 的 JAR 和 type `jar.sig` 的签名。`install`/`deploy` 同时处理原始主制品和
附加制品；私有业务交付流水线只应分发保护制品及签名，不能把原始 JAR 或 sources 一起交付。
更多参数见 [Maven 插件配置](artifact-plus-maven-plugin/README.md)。

## 签名密钥与部署校验

签名采用 JDK 的标准 **RSA-PSS / SHA-256 / MGF1-SHA-256 / saltLength 32**，要求 RSA 至少 3072 位。
私钥文件格式为 PKCS#8 PEM/DER，公钥为 X.509 SubjectPublicKeyInfo PEM/DER（不是证书文件）。
不接受把私钥放进业务配置、源码、JAR、命令行参数或公共制品目录。

构建环境通过受控秘密挂载提供私钥文件，通过独立可信配置提供公钥文件；插件默认读取两个路径环境变量：

```text
ARTIFACT_SIGNING_PRIVATE_KEY_FILE=<受控挂载的私钥文件路径>
ARTIFACT_SIGNING_PUBLIC_KEY_FILE=<构建端可信公钥文件路径>
```

这两个变量只保存路径，不保存密钥内容。混淆成功后，对最终 JAR 流式签名，并立即用公钥验签，
公私钥不匹配会使构建失败。签名是原始二进制的独立 `.sig` 文件，覆盖 JAR 全部字节，包括 ZIP 元数据。
它与 Maven Central 的 GPG 发布签名、`jarsigner` 内嵌签名是不同的格式和用途。

部署端预置可信的 `artifact-plus-core` 验签工具与公钥，显式校验：

```bash
java -jar /opt/artifact-verifier/artifact-plus-core-1.0.0.jar \
  /opt/releases/business.jar \
  /opt/releases/business.jar.sig \
  /etc/artifact-trust/business-public.pem
```

退出码 0 表示验签成功，其他退出码必须阻止部署和启动。验签工具与可信公钥必须由部署系统独立管理，
不能接受待验 JAR 自带的“可信公钥”，也不能提供业务配置开关绕过部署验签。
验签成功后使用同一不可变 JAR：不要在可由低权限用户替换文件的目录中“先验签再启动”。
没有部署端执行这一步，签名本身不会阻止 Java 启动未签名的 JAR。

## 混淆范围与保留规则

ProGuard 7.10.0 作为独立构建进程运行，不进入业务运行时。只对显式指定业务包及其子包处理；
`com.example.business` 与 `com.example.business.**` 等价，不接受任意 ProGuard 模式或全局通配符。
业务多版本 JAR、业务 `module-info.class`、已有 JAR 内嵌签名不进入混淆流程，可单独启用完整制品签名。

默认保留所有类名、公开和 protected 成员、接口、枚举、Record、Serializable 实现，以及带注解的类
和成员；保留运行时注解、泛型与参数元数据，不进行裁剪和优化。普通 Spring Bean、JPA 实体等通常
会被保留，混淆主要发生在未标注注解的业务内部辅助类成员上。业务包外成员保持名称。
这是一种保守的成员级混淆，不承诺所有业务代码都会被重命名，也不等于字节码加密。

基于字符串反射访问的私有字段、未标注注解的 DTO、Jackson 自定义可见性、模板/表达式访问及外部 SPI
需要业务项目补充规则，例如：

```proguard
-keep class com.example.business.dto.** { *; }
-keepclassmembers class com.example.business.ReflectionTarget {
    private java.lang.String lookup(java.lang.String);
}
```

额外文件只接受带完整 `{ }` 成员块的 `keep` 系列规则；支持类/成员注解、通配符及
`<init>`、`<fields>`、`<methods>`。不接受选项修饰符、文件引用、系统属性替换或其他工具指令，
实际读取上限为 1 MiB。缺少解析依赖、业务包
无匹配、工具执行失败都中止构建，不会忽略错误退回原始 JAR。Boot 嵌套依赖保持原始字节及 STORED
存储方式，资源原样复制。资源内容不加密；业务秘密仍需来自外部配置。

`mapping.txt` 仅供内部排障，按制品版本安全归档，不与 JAR 分发。保留规则并不能代替业务验收：
发布前用保护后的 JAR 验证应用启动、反射、持久化、序列化及关键业务链路。

## 失败与输出管理

保护总开关默认关闭，混淆和签名独立开关也默认关闭。总开关开启但没有选择任何能力会报错。
关闭开关会清理当前配置的保护输出、签名和映射；处理失败同样清理，输入文件不变。
输出不能与输入、密钥、规则或依赖路径重合，也不能是它们的符号链接/硬链接别名。
每个构建任务独占输出目录；不要并发写入同一路径。修改输出路径或切换版本后，应以 `clean` 清理旧目录。
混淆和重打包统一读取临时输入快照；复制期间检测到输入状态变化会失败。输入及构建目录应由当前构建独占，
临时快照不替代操作系统权限隔离。混淆工具最多运行 10 分钟，错误诊断仅保留首尾共 32 KiB，超时或中断会回收进程。

构建、测试及制品验证按仓库要求在 GitHub Linux CI 进行。`buildAll` 覆盖 JUnit/TestKit，
`verify-artifact-maven.py` 使用隔离仓库验证 Maven/Boot 消费者、附加制品和 OpenSSL 签名互通。

## 工具来源

- [ProGuard](https://github.com/Guardsquare/proguard/tree/v7.10.0)：独立混淆工具，遵循其 GPL-2.0 许可；本项目不复制其源码或嵌入其实现。
- [JDK RSA-PSS 参数](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/PSSParameterSpec.html)：签名采用标准 JCA 算法。
- [Gradle 插件开发](https://docs.gradle.org/current/userguide/custom_plugins.html)：构建插件与普通运行时依赖分离。
