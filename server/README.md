# 次元委托 Java 服务端

Java 11、Spring Boot 2.7、JDBC、H2 文件数据库和 Flyway 迁移。启动时自动建立 `server/data/weituolaoshi.mv.db` 并执行 `V1` 建表、`V2` 演示数据迁移；不需要预先安装数据库。数据模型见 `docs/data-model.md`。

## 运行

使用 JDK 11 和 Maven 3.6+：

```sh
cd server
mvn spring-boot:run
```

服务默认监听 `http://localhost:8080`。本地演示用户为 `demo-customer` 和 `demo-coser-f`：

```sh
curl http://localhost:8080/v1/services
curl -X POST http://localhost:8080/v1/orders/quote \
  -H 'Content-Type: application/json' -H 'X-Demo-User: demo-customer' \
  -d '{"serviceId":"svc-1","startAt":"2026-11-16T14:00:00","durationHours":3}'
```

报价金额单位为分，服务端计算并持久化，十分钟过期。新发布的服务只进入 `pending_review`，不会自动公开。可用演示报价创建待支付订单：

```sh
curl -X POST http://localhost:8080/v1/orders \
  -H 'Content-Type: application/json' -H 'X-Demo-User: demo-customer' \
  -H 'Idempotency-Key: booking-001' \
  -d '{"quoteId":"上一步返回的quoteId","venueId":"venue-demo-expo"}'
```

订单创建在事务中复核已存的实名结论、同性别、档期、地点和24小时内的聊天确认，并写入订单事件及幂等记录。演示档期为2026年11月16日10:00–22:00，地点为国家会展中心。支付、退款及结算仍返回 501。`X-Demo-User` 只供本地演示，不能作为生产认证；演示地点没有接入真实 POI 营业时间数据。小程序的订单等非账号页面目前仍使用本地缓存。

生产环境应改用 PostgreSQL，移除演示数据迁移和演示身份头，并接入符合要求的实名供应商、对象存储、内容审核、支付及消息服务。

### 微信账号

启动服务前设置 `WECHAT_APP_ID` 和 `WECHAT_APP_SECRET`（小程序的 AppID 与 AppSecret）。
将 `wechat-miniprogram/app.js` 中的 `apiBaseUrl` 设为服务端 HTTPS 地址，并在微信公众平台将该域名配置为 request、uploadFile 和 downloadFile 合法域名。
个人页使用 `wx.login` 换取 30 天会话，可修改昵称（1–30 字）及上传不超过 2MB 的 JPEG、PNG 或 WebP 头像。
头像默认保存在 `./data/avatars`，生产环境可通过 `AVATAR_DIRECTORY` 指向持久目录。

### 实名认证

设置 `IDENTITY_PROVIDER_URL`、`IDENTITY_PROVIDER_KEY` 和随机生成的 `IDENTITY_DIGEST_KEY`。服务端向核验服务发送 `POST` JSON：`{"name":"...","idNumber":"..."}`，请求头为 `Authorization: Bearer <key>`；核验服务须返回 `{"verified":true,"reference":"核验流水号"}` 才会通过。未配置或核验失败时不会写入已认证状态。前端不缓存完整证件号码，数据库只保存脱敏号码、带密钥摘要、核验流水号及成年/性别结论。供应商应由项目方选择并完成相应合规接入。

普通用户核验通过后立即生效；Coser 核验通过后进入 `pending`，需要后台审核。小程序从 `/v1/identity/me` 同步最新状态。

### 管理后台

首次启动前设置 `ADMIN_BOOTSTRAP_USER` 和至少 12 位的 `ADMIN_BOOTSTRAP_PASSWORD`；只有管理员表为空时才创建首位超级管理员。访问 `/admin/index.html`。后台同源调用 `/admin/api`，使用独立的 8 小时管理员会话。

内置超级管理员、认证审核员、只读管理员。超级管理员可创建管理员、禁用账号、重置密码、分配角色，新增角色并配置权限；每个接口都在服务端检查权限。认证审核、管理员和角色变更写入审计日志。生产部署应使用 HTTPS，并将后台置于受控访问环境。
