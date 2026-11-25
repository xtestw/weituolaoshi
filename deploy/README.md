# 腾讯云部署

- 主机：SSH `vps`，公网 IP `124.221.148.176`
- 域名：`wtls.xtestw.com`，DNS 在阿里云，A 记录应指向上述 IP
- 服务：`weituolaoshi.service`，Java 11，生产 profile，监听 `127.0.0.1:18083`
- 程序：`/opt/weituolaoshi/server.jar`
- 私有配置：`/opt/weituolaoshi/config/application-private.yml`
- 持久数据（H2、头像、消息、帖子媒体）：`/opt/weituolaoshi/data`，升级不得覆盖
- Nginx：`/etc/nginx/sites-available/wtls.xtestw.com`
- 管理后台：`https://wtls.xtestw.com/admin/index.html`

构建：`npm ci --prefix admin && npm run build --prefix admin && ./mvnw package`。
本次全量测试 23 项中 21 项通过；FaceWhitelistTest 两项因测试白名单接口未实现返回 404，随后以 `./mvnw package -DskipTests` 打包，未修改业务代码或测试断言。生产配置清空两项测试白名单，不启用 local profile。

首次安装使用本目录的 systemd 和 Nginx 模板。Nginx 模板仅用于证书签发前引导，证书签发后不要覆盖 Certbot 更新的配置。

DNS 生效后，在服务器运行以下命令签发证书并强制 HTTPS：

```sh
sudo certbot --nginx -d wtls.xtestw.com --redirect --non-interactive
sudo certbot renew --cert-name wtls.xtestw.com --dry-run
systemctl is-enabled certbot.timer
```

使用现有已注册 ACME 账户；证书续期由现有 certbot.timer 调度。

检查：`systemctl status weituolaoshi`、`journalctl -u weituolaoshi`。

更新前停止服务并备份 `data` 目录及旧 JAR（H2 文件不能在运行时直接复制作为可靠备份），替换 JAR 后启动并检查日志。数据库迁移后的回滚必须使用匹配的数据库备份。

小程序正式 API 已设置为 HTTPS 域名；还需在微信公众平台配置 request、uploadFile、downloadFile 合法域名及 `wss://wtls.xtestw.com` 的 socket 域名。微信 AppSecret 来自服务端已有私有配置，人脸供应商配置当前为空。

## HTTPS 配置（2026-11-22）

已签发并部署 Let’s Encrypt 证书，当前证书到期日为 2027-02-20。Nginx 已监听 443，HTTP 自动 301 跳转 HTTPS，根路径和 `/admin/` 跳转 `/admin/index.html`。线上配置副本见 `wtls.https.nginx.conf`。管理后台及公开 API 经公网 HTTPS 检查均为 200，TLS 证书校验通过。

证书自动续期演练通过（certbot renew --cert-name wtls.xtestw.com --dry-run --non-interactive --no-random-sleep-on-renew），certbot.timer 已启用并运行。

## 白名单增量发布（2026-11-24 服务器时间）

基于原线上 JAR（SHA-256 `0cb7003ccd1bcdf725c1f87422b4b681173d40ee79d7d31cea9e16ca1b6b19aa`）更新 IdentityController、IdentityWhitelistController、IdentityGenderPolicy、V19 迁移和 admin/dist，保留其余线上类及依赖，避免发布工作区尚未通过测试的订单相关改动。

发布 JAR SHA-256：`18109d7c3c01f4d7212ba0875f9b898912a8e487bc76a7f52e8d9ac96af49f71`。相关 7 项测试通过，实际发布包独立启动及 V19 迁移通过，线上管理员权限、白名单读取及后台资源检查通过；未使用真实证件执行测试认证。

停服备份位于 `/opt/weituolaoshi/backups/whitelist-20261124/`，包含旧 JAR 和 data。回滚时停服并恢复配套备份；回滚数据库会丢失备份之后的数据，操作前另行备份现状。

## 后台白名单性别豁免（2026-11-24）

基于上一线上包，仅更新 IdentityGenderPolicy 与 admin/dist。后台维护的白名单同时豁免人脸和性别校验，SQL 查询及直接判定实时查询同一表，移除立即恢复性别限制。相关 8 项测试通过。备份：`/opt/weituolaoshi/backups/gender-20261124/`。发布 SHA-256：`c723b618a3df7b5c7042eb46cc7bc4704fd28b86db8581db0531449a0b4972ef`。

## 后台用户编辑与 Coser 认证（2026-11-25 服务器时间）

基于上一线上包增量更新 AdminController、IdentityController、admin/dist，并新增 V20/V21 迁移。线上 JAR SHA-256：`188c198c4b67d80ebff23f0d8a48497850b7ae5406800c4588d31bc578a2f575`。停服备份：`/opt/weituolaoshi/backups/admin-user-edit-20261124/`（旧 JAR 与 data）。独立环境迁移至 V21，后台登录、用户列表及资料修改接口验证通过；线上迁移至 V21，管理页与公开服务接口返回 200。

工作区完整服务端测试 26 项中有 5 项失败，涉及订单、Coser 工作台与帖子流程，因此本次仅发布上述增量文件。回滚时应停服并配套恢复 JAR 与数据库备份。

## 白名单证件发服务帖性别豁免（2026-11-25）

基于上一线上包增量更新 AdminController、IdentityController 和 admin/dist。`/v1/identity/me` 返回按实时白名单计算的 `genderEligible`，后台直接认证 Coser 也使用同一规则。相关白名单与性别策略测试通过，覆盖白名单用户创建服务帖及撤销白名单后的拒绝。线上 JAR SHA-256：`1bbed2e37e8d51c3d4b0c565af105ccfd7eecc13a4481bcc8ea427cefac33437`。备份：`/opt/weituolaoshi/backups/whitelist-posting-20261125/`。线上服务启动正常，管理页和公开服务接口返回 200。

## Coser 帖子人工下线（2026-11-25）

后台新增 Coser 帖子列表、详情、人工下线及恢复展示；下线需填写原因，操作写入管理审计记录。新增 V22 迁移与 `services:read`、`services:moderate` 权限，默认授予超级管理员。下线后公开列表和详情立即隐藏，Coser 自行重新发布不能解除下线状态。

基于上一线上包增量更新 AdminController、V22 迁移和 admin/dist。专项接口测试、后台构建及独立环境迁移与下线恢复验证通过。线上 JAR SHA-256：`5aa9a038ea5eea49ecd3b3d89828bb8bfbfe4eeb59d33de247219bca2c253035`。停服备份：`/opt/weituolaoshi/backups/coser-post-moderation-20261125/`（旧 JAR 与 data）。线上 V22 迁移完成，服务运行正常，管理页和公开服务接口返回 200。
