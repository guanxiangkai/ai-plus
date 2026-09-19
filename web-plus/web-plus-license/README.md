# Web Plus License

`web-plus-license` 提供不依赖 Spring 和 Web Plus 其他模块的 PS256 许可证内核。

许可证签发端通过 `LicenseKeys.readPrivateKey(...)` 读取至少 3072 位的 PKCS#8 RSA 私钥，调用
`LicenseTokens.issue(claims, key)` 签发令牌。运行端通过 `LicenseKeys.readPublicKey(...)` 读取 X.509
SPKI 公钥，并以产品、实例、模式和时钟创建 `LicenseTokens` 后调用 `verify(token, expectedNonce)`。

在线许可证最长 15 分钟，`expectedNonce` 必须是本次请求保存的随机值；离线许可证的 nonce 和
`expectedNonce` 都必须为空字符串。内核不生成密钥、不访问网络，也不维护 nonce 或时钟缓存。
