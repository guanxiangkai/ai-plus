# Artifact Plus Maven Plugin

该插件在 Maven `verify` 阶段为已完成 `package`（包括 Spring Boot `repackage`）的 JAR 生成独立保护输出。原始 JAR 保持为主制品；启用后，受保护 JAR 以 classifier `protected` 附加，签名以 type `jar.sig`、classifier `protected` 附加。

业务应用只在构建时声明此插件。运行时依赖中不包含插件或 ProGuard。

```xml
<plugin>
  <groupId>io.github.guanxiangkai</groupId>
  <artifactId>artifact-plus-maven-plugin</artifactId>
  <version>请使用发布版本</version>
  <executions>
    <execution>
      <goals><goal>protect</goal></goals>
    </execution>
  </executions>
  <configuration>
    <packages>
      <package>com.example.application</package>
    </packages>
    <!-- 有额外保留规则时再配置 keepRules，路径必须存在。 -->
  </configuration>
</plugin>
```

```bash
mvn clean verify -Dartifact.protection.enabled=true -Dartifact.obfuscation.enabled=true -Dartifact.signing.enabled=true
```

默认输入为 `${project.build.directory}/${project.build.finalName}.jar`，输出为 `${project.build.directory}/protected/${project.build.finalName}.jar`，映射文件为 `${project.build.directory}/protected/mapping.txt`。签名私钥和公钥默认从 `ARTIFACT_SIGNING_PRIVATE_KEY_FILE` 与 `ARTIFACT_SIGNING_PUBLIC_KEY_FILE` 指向的外部文件读取，也可以通过 `privateKeyFile` 与 `publicKeyFile` 显式配置。

`enabled`、`obfuscationEnabled`、`signingEnabled` 默认均为 `false`，可分别用 Maven 属性 `artifact.protection.enabled`、`artifact.obfuscation.enabled`、`artifact.signing.enabled` 覆盖。关闭时核心会清理本次保护输出且不附加制品。签名覆盖最终输出 JAR 的全部字节；部署端应使用外部受信任公钥验签后，将已验证的 JAR 作为不可变文件使用。
