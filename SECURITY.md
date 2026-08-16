# 安全策略

## 支持范围

安全修复只面向仍受维护的最新稳定分支和已发布模块版本。历史分支、示例配置和未发布快照
不承诺单独回补；受影响版本与修复版本会在安全公告中明确列出。

## 报告漏洞

请通过 GitHub 仓库的 **Security → Report a vulnerability** 私密报告功能提交问题，不要先创建
公开 Issue，也不要附带真实生产凭据、个人信息、内部地址或客户数据。报告应尽量包含受影响模块、
版本、最小复现步骤、影响判断和建议缓解措施。

维护者完成初步确认前，请不要公开可利用细节。若 GitHub 私密漏洞报告不可用，可先创建一个
不含利用细节和敏感数据的普通 Issue，请求维护者开启私密沟通渠道。

## 凭据泄露

若怀疑 Token、密钥或密码已经泄露，应先在其权威系统中完成影响识别与轮换，再处理源码和 Git
历史；仅删除当前文件不能使历史对象或已下载副本失效。

## PowerJob Worker 依赖说明

`web-plus-job` 使用 PowerJob 5.1.2 Worker SDK。依赖扫描会因其共享的 `powerjob-common` 包报告
[CVE-2025-14518](https://nvd.nist.gov/vuln/detail/CVE-2025-14518)，但该漏洞入口位于 PowerJob
Server 的 `/server/checkConnectivity` Controller；本模块不包含 Server Controller，也不向外暴露该
入口，因此当前发布边界不存在已公开的利用路径。

这一判断不覆盖消费方自行引入 PowerJob Server、直接调用 `PingPongUtils` 或新增同类网络探测接口的
场景。消费方若部署 Server，必须按上游 [Issue #1144](https://github.com/PowerJob/PowerJob/issues/1144)
跟踪修复，并在修复版发布前实施接口阻断和最小出站网络策略。
