# 次元委托 Java 服务端

Java 11、Spring Boot 2.7、JDBC、H2 文件数据库和 Flyway 迁移。启动时自动建立 `server/data/weituolaoshi.mv.db` 并执行迁移；不需要预先安装数据库。数据模型见 `docs/data-model.md`，新增的 MySQL v4 资料与 V6 迁移对应关系见 `docs/v4-schema-integration.md`。

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

订单创建在事务中复核已存的成年女性实名结论、档期、地点和24小时内的Coser时间确认，并写入订单事件及幂等记录；Coser不再需要缴纳保证金。可浏览的男性或未成年人不能下单或发布服务。服务时间限制为上海时间07:00–21:00、不可过夜。演示档期与地点仍来自本地夹具；支付、退款及结算仍返回 501。`X-Demo-User` 只供本地演示，不能作为生产认证；演示地点没有接入真实 POI 营业时间数据。小程序的订单等非账号页面目前仍使用本地缓存。聊天及Coser时间确认的写入接口尚未实现，因此真实的24小时内预约链路仍需在消息模块完成后才能开放。

生产环境应改用 PostgreSQL，移除演示数据迁移和演示身份头，并接入符合要求的实名供应商、对象存储、内容审核、支付及消息服务。

### 微信账号

启动服务前设置 `WECHAT_APP_ID` 和 `WECHAT_APP_SECRET`（小程序的 AppID 与 AppSecret）。
将 `wechat-miniprogram/app.js` 中的 `apiBaseUrl` 设为服务端 HTTPS 地址，并在微信公众平台将该域名配置为 request、uploadFile 和 downloadFile 合法域名。
个人页使用 `wx.login` 换取 30 天会话；首次登录时以微信 OpenID 自动注册平台账号，后续登录复用账号。小程序缓存会话，401 时清除并引导重新登录，可主动退出。微信不再自动提供昵称、头像，因此新账号默认显示“微信用户”；账号可修改昵称（1–30 字）及上传不超过 2MB 的 JPEG、PNG 或 WebP 头像。认证、订单等其他页面尚有本地演示状态，不能视为已完成全链路账号接入。
头像默认保存在 `./data/avatars`，生产环境可通过 `AVATAR_DIRECTORY` 指向持久目录。

### Coser 档期（已接入服务端）

已认证且账号正常的成年女性 Coser 可通过会话令牌管理按日档期；默认不可约，开放日对应上海时间 07:00–21:00，不支持夜间或过夜。已被有效订单占用的日期不能关闭或重设。可设置范围为本月至下一自然年 12 月。

```text
GET  /v1/cosers/me/availability?month=2026-11
PUT  /v1/cosers/me/availability/2026-11-20  {"open":true}
POST /v1/cosers/me/availability/batch       {"month":"2026-11","type":"weekdays_open"}
```

批量类型为 `all_open`、`all_closed`、`weekdays_open`、`weekends_open`；工作日和周末操作只修改匹配的日期，过去日期不变。读接口返回该月每天的 `date` 和 `open`。小程序档期页面已接入这些接口；未登录或账号不是已审核 Coser 时，服务端拒绝修改。

### 实名认证

设置 `IDENTITY_PROVIDER_URL`、`IDENTITY_PROVIDER_KEY` 和随机生成的 `IDENTITY_DIGEST_KEY`。服务端向核验服务发送 `POST` JSON：`{"name":"...","idNumber":"..."}`，请求头为 `Authorization: Bearer <key>`；核验服务须返回 `{"verified":true,"reference":"核验流水号"}` 才会通过。未配置或核验失败时不会写入已认证状态。前端不缓存完整证件号码，数据库只保存脱敏号码、带密钥摘要、核验流水号及成年/性别结论。供应商应由项目方选择并完成相应合规接入。

普通用户和 Coser 均在实名供应商核验通过后立即生效；Coser 同时开通接单资料，无需后台人工审核。旧的待审核记录需重新提交核验。小程序从 `/v1/identity/me` 同步最新状态。腾讯云 `IdCardVerification` 可用于姓名与身份证号二要素核验，但当前服务端使用通用供应商适配接口，尚需接入腾讯云签名调用与开通凭证。二要素核验不能证明当前操作人就是证件持有人；若业务要求本人真人认证，应另接腾讯云人脸核身流程。

### 管理后台

首次启动前设置 `ADMIN_BOOTSTRAP_USER` 和至少 12 位的 `ADMIN_BOOTSTRAP_PASSWORD`；只有管理员表为空时才创建首位超级管理员。访问 `/admin/index.html`。后台同源调用 `/admin/api`，使用独立的 8 小时管理员会话。

内置超级管理员、认证查看员、只读管理员。超级管理员可创建管理员、禁用账号、重置密码、分配角色，新增角色并配置权限；每个接口都在服务端检查权限。管理员和角色变更写入审计日志。生产部署应使用 HTTPS，并将后台置于受控访问环境。

### 管理后台前端

管理后台位于 `admin/`，使用 React、Ant Design 和 Vite。修改后台页面后运行：

```bash
npm ci --prefix admin
npm run build --prefix admin
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Spring Boot 从 `admin/dist` 加载构建后的静态资源，访问 `http://127.0.0.1:18080/admin/index.html`。开发时也可运行 `npm run dev --prefix admin`，Vite 会将 `/admin/api` 代理到本地后端。提交前请一并提交 `admin/dist`，以便仅使用 Maven 的环境也能启动管理后台。
