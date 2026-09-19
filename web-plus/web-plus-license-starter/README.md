# 业务软件授权

本模块支持签名离线许可证和在线短期租约。引入即强制授权：首次验证失败阻止启动，WebFlux 新请求
在授权失效后返回 HTTP 403。它与登录鉴权、Artifact Plus 的构建签名是不同的边界。

**本地授权代码能够被控制运行机器的人修改或跳过。** 即使加入混淆、加密 ClassLoader、硬件标识或
Native Image，也不能据此承诺“没有授权绝对破解不了”。若核心能力必须不能在未授权状态下使用，
应让你控制的服务端持有并执行关键算法、数据或不可替代的操作，服务端在每次调用时自行鉴权和授权，
不能信任客户端上报的 `licensed=true`。仅在线下发解密密钥或返回许可布尔值，仍不能保护已交付的代码。

## 按项目选择

业务项目需要授权时显式加入：

```kotlin
dependencies {
    implementation("io.github.guanxiangkai:web-plus-license-starter:1.0.0")
}
```

Maven 使用相同坐标。该模块不自动包含在 `web-plus-starter` 中，不影响没有选择授权能力的业务项目。
未授权的开发构建通过不引入该 starter 表达；部署版本引入后不提供 `enabled=false` 开关。
这只是防止误配置关闭校验，不能阻止攻击者替换依赖或排除自动配置。发布管线应独立校验制品签名和依赖策略。
这些坐标是本仓库未发布模块的初始版本，需要 Linux CI 验证并正式发布后才能通过 Central 消费。

### 离线模式

```yaml
web-plus:
  license:
    mode: OFFLINE
    issuer: example-vendor
    subject: example-customer
    product: example-product
    instance: example-installation
    public-key: /etc/business-trust/license-public.pem
    license-file: /etc/business-license/license.jwt
    required-features:
      - reports
```

公钥是至少 3072 位 RSA 的 X.509 SubjectPublicKeyInfo PEM/DER，独立可信配置，不从许可证中读取。
JWT 固定为 PS256，类型为 `ai-plus-license+jwt`，绑定发行者、客户、产品、实例、功能和生效/到期时间。
不接受其他算法、压缩、无签名令牌、远程密钥地址或令牌自带公钥。私钥只在发行端以受控文件形式保存。

离线文件在启动时读取；更换授权文件后重启应用。到期仍会在每个关口检查。单调时间防止当前进程内
回拨墙钟延长已加载授权，但不能防御重启后回拨时钟或恢复整机快照。离线不能即时撤销许可证。
`instance` 是安装标识而非硬件证明；可控制机器的人能复制或伪造该值，不能据此承诺防复制。

### 在线模式

```yaml
web-plus:
  license:
    mode: ONLINE
    issuer: example-vendor
    subject: example-customer
    product: example-product
    instance: example-installation
    public-key: /etc/business-trust/license-public.pem
    lease-endpoint: https://license.example.com/v1/leases
    credential-file: /run/secrets/license-client-token
    refresh-interval: 30s
    request-timeout: 5s
    required-features:
      - reports
```

在线模式不配置 `license-file`，离线模式不配置在线地址或凭据；混合配置会失败。
只接受没有用户信息、查询串和片段的 HTTPS 地址，不跟随重定向。凭据内容由受控文件读取，配置只保存路径。
不关闭 TLS 证书和主机名验证。首次启动必须成功续租，租约不写磁盘，每次启动都需要重新联系授权服务。

每次续租生成 32 字节随机 nonce；响应必须带同一个 nonce，且符合指定的 ONLINE 模式、产品和实例。
签名租约有效期最长 15 分钟。授权服务应签发比 `refresh-interval + request-timeout` 更长的租约，例如
有效期 5 分钟、间隔 30 秒。间隔范围 1 秒至 5 分钟，超时范围 1 至 30 秒，且超时必须小于间隔。

- 明确拒绝、非 200 响应（5xx 除外）、错误类型、非法签名或 nonce 不符：清空授权。
- 网络故障或 5xx：仅沿用还未到期的原租约，到期立即拒绝；没有额外宽限期，也不切换为离线模式。
- 正常撤销最多需要等待下一次成功续租检查；服务端不可达时，已发出的签名租约可持续到其到期。
  已开始的调用不会被强制中断。需要逐次即时撤销的关键能力，应在你控制的服务端逐次检查。

## 授权服务协议与签发

本仓库提供客户端及签发 API，**没有部署授权服务器，也没有客户、许可订单、撤销表或实例注册数据库**。
在线地址需要接入实际由你控制的服务；不能把下面的签发 API 包成一个无认证的公共端点。

客户端请求：HTTPS `POST`，`Authorization: Bearer <文件中的凭据>`，
`Content-Type: application/x-www-form-urlencoded`，表单字段为 `product`、`subject`、`instance`、`nonce`。
响应成功时是 HTTP 200、`Content-Type: application/jwt`、正文为一个紧凑 JWS，最大 64 KiB。
撤销、客户不匹配或实例不允许时返回 403。客户、产品和实例权限必须从服务端权威记录确定；客户端字段仅作为请求。
服务端需要凭据管理、速率限制、实例/席位策略、审计和撤销机制，不能仅凭客户端声称的实例标识发证。

发行端只需依赖 `web-plus-license`，在已经完成授权判定之后签发：

```java
var now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
var claims = new LicenseClaims(
        "license-record-id", "example-vendor", "example-customer", "example-product",
        "example-installation", LicenseMode.ONLINE,
        now, now, now.plusSeconds(300), java.util.Set.of("reports"), requestNonce);
String jwt = LicenseTokens.issue(claims,
        LicenseKeys.readPrivateKey(java.nio.file.Path.of("/run/secrets/license-signing-key.pem")));
```

离线签发使用 `LicenseMode.OFFLINE`、空 nonce 和明确的到期时间，将返回的 JWT 作为 `license.jwt` 交付。
这里的签名私钥必须是发行方受控的 PKCS#8 PEM/DER，绝不能进入客户 JAR、配置、在线响应或日志。
授权签名密钥与登录 Token、制品签名密钥分别管理；同一受信公钥上下文不从 JWT `kid/jku/jwk/x5u` 动态选取。
示例只描述接口，不包含真实凭据；当前回归代码仅使用临时测试密钥。

## 业务入口检查

自动配置只检查 WebFlux **新 HTTP 请求**。定时任务、消息消费、长连接后续命令，以及已有请求中的
后续关键操作必须在各自入口再次检查；仅加 HTTP 过滤器不能覆盖它们，也不能自动停止已运行的任务。

```java
// 构造器注入 LicenseGuard，在产生业务副作用之前检查。
licenseGuard.requireFeature("reports");
// 接着执行业务操作。长连接每条命令、任务每个受保护单元均应重新检查。
```

`current()` 要求当前许可证有效，`requireFeature(name)` 同时要求功能名精确匹配，无隐式通配符。
许可证不替代用户权限：一个拥有 reports 授权的产品，仍须检查当前用户是否可以读取该报告。
按租约自动执行的后台工作应在每次执行前调用此关口；已建立 WebSocket 的业务命令入口也必须调用。
这些消费方入口不在基础库仓库内，其实际接入验收是上线前必做项。

## 验证状态

测试源码覆盖签发验签、错上下文/签名/模式/nonce、时间边界、密钥和响应大小限制、启动失败、续租拒绝、
临时网络故障不续期、当前进程时钟回拨、配置装配与 HTTP 拒绝。本机未执行构建或测试，需 GitHub Linux CI 通过。
真实 HTTPS 授权服务、业务后台入口、实例策略和撤销时延仍需要实际消费项目验收。
