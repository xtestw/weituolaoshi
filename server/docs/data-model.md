# V1 数据模型

迁移脚本位于 `src/main/resources/db/migration`。金额一律使用人民币分和 `BIGINT`；预约时间按 `Asia/Shanghai` 解释。

| 领域 | 表 | 用途 |
|---|---|---|
| 用户与认证 | `app_user`, `identity_verification`, `coser_profile` | 用户、最少必要的实名结论、Coser资料及保证金资格 |
| 服务 | `service_post`, `service_image`, `service_boundary`, `availability_slot` | 帖子、图片、边界和档期 |
| 地点与报价 | `venue`, `price_quote` | 公共场所、服务端限时报价 |
| 订单 | `booking_order`, `order_event`, `idempotency_record` | 当前状态、不可修改的状态事件、重试去重 |
| 消息 | `conversation`, `chat_message`, `time_confirmation` | 会话、消息与紧急预约确认 |
| 资金 | `payment_transaction`, `settlement` | 支付及退款流水、Coser结算账单 |

`service_post` 属于 `coser_profile`；`price_quote` 连接客户和服务；`booking_order` 连接客户、Coser、服务、报价和地点；事件、资金流水和会话可关联订单。

数据库只预留实名供应商凭证、证件摘要、脱敏号码、成年和性别结论，不存证件原图或完整号码。当前实现服务查询/创建、档期查询、报价创建、待支付订单创建与订单读取；支付后的状态机在微信支付接入前保持关闭。`V3` 中的认证、档期和地点仅是本地演示数据。
