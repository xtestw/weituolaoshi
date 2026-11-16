# COS v4 数据库设计与现有库的对应关系

## 资料与适用范围

`reference/transfer/` 保存从 `spg-tencent:/home/ubuntu/transfer/` 下载的原件：

- `cos_business_v4_mysql.xlsx`：11 张业务表、237 个字段及关系、约束、业务规则清单。
- `cos_business_业务&表交互流程图.html`：发布、下单履约、取消退款的流程图。
- `cos_commission_schema.sql`：MySQL 8.0.16+ 空库建表方案，带应用事务协议。

这些文件是 **MySQL 目标设计**，不是当前 H2 数据库的 Flyway 迁移，不能在已有库直接执行。空库方案已在本地 MySQL 8.4 容器中执行，确认生成 11 张业务表。当前应用继续使用字符串主键、人民币分 `BIGINT` 和既有 API。`V6__marketplace_design.sql` 是针对当前库的增量迁移，保留已有数据和接口。

## 表对应关系

| MySQL v4 目标 | 当前库（V6 后） | 增量设计 |
|---|---|---|
| `marketplace_user` | `app_user` | 提供方主体、账号状态、注销时间、头像对象键、性别展示码、同性委托开关、版本号；实名结论仍在 `identity_verification` |
| `marketplace_author` | `coser_profile` | 接单/认证状态、基础小时价、时区、预约步长和缓冲策略 |
| `marketplace_author_role` | `coser_role` | 一个 Coser 多个 IP 角色；同作者 IP + 角色唯一；帖子和订单用复合外键约束归属 |
| `marketplace_content` | `service_post` | 展示/委托类别、角色和 IP 快照、封面、可见性、审核状态、版本和软删除 |
| `marketplace_content_media` | `service_media` | 图片/视频、对象键、缩略图、稳定入口、处理与审核状态、排序；旧 `service_image` 保留兼容 |
| `marketplace_availability` | `availability_slot` | 作者级开放窗口、时区和版本；占用由预约计算，不在窗口上设 `is_booked` |
| `marketplace_order` | `booking_order` | 帖子/角色/价格/条款/媒体快照，付款与退款汇总，取消政策及结果快照 |
| `marketplace_appointment` | `order_appointment` | 一单一预约，服务时间与含缓冲的占用时间分开；占档、确认、释放时间与状态 |
| `marketplace_review` | `service_review` | 每单一次评分、匿名、图片、审核和 Coser 回复 |
| `marketplace_refund` | `order_refund` | 独立退款流水、金额依据、渠道状态和幂等键；`payment_transaction` 保留为渠道尝试表 |
| `marketplace_order_event` | `order_event` | 事件幂等键、操作人类别、原因和实际发生时间 |

当前库另有 `venue`、`price_quote`、`idempotency_record`、聊天、结算、管理员和实名认证表。它们不在来源的 11 表交付范围内，保留既有职责。

## 金额与时间

现有运行库使用 `BIGINT` 人民币分，V6 新金额字段沿用此单位，避免与已运行的报价/订单计算混用。来源模型使用 `DECIMAL(18,4)` 主币单位、多币种 `currency_scale`，**不能通过这次增量迁移视为已支持多币种**。迁至 MySQL 目标模型前，应确定币种字典、历史金额换算、舍入和回填方案，并做双写/对账验证。

来源要求 UTC 存储及 IANA 时区展示。现有 API 仍把预约时间按 `Asia/Shanghai` 解析；V6 增加时区快照字段，但不自动改写既有时间。迁移到统一 UTC 前，需要逐条转换旧预约并处理夏令时歧义。

## 必须由应用事务实现的规则

DDL 负责字段、外键、枚举、范围和唯一键；它不能自行完成以下流程：

1. 下单、支付确认、取消、退款和排期编辑都先锁同一 `coser_profile` 行，再以统一顺序锁窗口、角色、帖子、订单、预约和退款。对同一 Coser 的新占用比较 `occupied_start_at < new.occupied_end_at AND occupied_end_at > new.occupied_start_at`，只把未过期占档、待确认及已确认的预约当作有效占用。
2. 新订单只能选审核通过、公开上架的委托帖，角色必须属于该 Coser 且启用；服务及前后缓冲都在开放窗口内。最少提前 24 小时，付款期限 15 分钟，付款后确认期限 2 小时，确认截止还须早于占用开始。下单固化帖版本、角色、IP、单价、条款、媒体和政策快照。
3. 支付成功必须来自渠道验签或主动查询。迟到支付不复活已释放的占档，应记真实收款并发起全额退款。Coser 拒单、确认超时和服务开始前 Coser 取消均全退。
4. 单主取消已确认且未开始的预约：距服务开始不少于 48 小时全退；24–48 小时退实付的 50%；不足 24 小时不退。未确认前单主取消全退。已开始、爽约或争议转人工。计算基数是实付金额；核定应退、已到账退款和取消费用分别记录，不能把取消费用当作平台收入。
5. 退款按同一业务请求幂等，渠道结果未知保持处理中并主动查询；仅渠道确认成功后累加 `refunded_amount`，且不得超过实付或核定应退。失败不恢复订单或预约。服务完成由单主确认，未确认转人工，不自动完成或结算。一期禁止改期，换时间须取消旧单再新建。
6. 只有已就绪、审核通过、未删除的媒体可展示；封面取最小排序的合格媒体并与媒体变更同事务更新。稳定 URL 仍须访问鉴权，不持久化临时签名 URL。匿名评价不能泄露单主 ID；精确地点须加密和最小授权。

## 当前边界

V6 是兼容迁移，新增列允许旧记录为空；旧 `service_post` 未被可靠地映射到 IP 角色，因此没有强制历史委托帖 `role_id NOT NULL`。已实现真实报价、订单创建、档期占用和同一 Coser 的下单串行化，但仅覆盖下单起点；支付、取消、退款、评价和媒体 API 尚未实现来源文档的完整事务协议。演示用 `X-Demo-User` 仅在 `local` 配置生效。不能只因表已建好就开放完整交易流程。上线前应补对应服务代码、状态机、对账和并发测试，再逐步加严非空与复合约束。
