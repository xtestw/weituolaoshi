# V1 服务端状态机与接口约束

## 订单状态

```text
draft
  -> awaiting_payment
  -> paid_pending_coser
  -> awaiting_service
  -> customer_completed / coser_completed
  -> completed
  -> settlement_pending
  -> settled

任一允许状态
  -> cancel_requested
  -> cancel_agreed / dispute_review
  -> refund_pending / compensation_pending
  -> cancelled
```

所有状态变化必须由服务端校验当前状态与操作者角色，写入不可修改的事件日志，并使用幂等键防止支付回调、重复点击或网络重试造成重复扣款、重复退款和重复结算。

## 下单资格

服务端创建订单前必须重新校验：

1. 用户与Coser均实名认证且年满18周岁。
2. 两方证件性别相同；接口只返回允许/拒绝，不返回对方证件字段。
3. Coser帖子已审核并处于可交易状态。
4. 预约区间不跨自然日、不属于过夜时段，并包含在Coser可接单档期内。
5. 对应档期无其他订单锁；创建待支付订单时使用短期锁，支付成功后改为履约锁。
6. POI属于平台允许的公共场所分类，且预约时段处于营业时间。
7. 距履约不足24小时的订单存在双方聊天确认记录。
8. Coser已缴纳的平台保证金达到接单要求。

## 身份数据返回模型

```json
{
  "verificationId": "iv_xxx",
  "status": "verified",
  "adult": true,
  "legalGender": "F",
  "maskedDocumentNumber": "310***********182",
  "verifiedAt": "2026-11-10T12:00:00+08:00"
}
```

前端永远不接收身份证原图或完整证件号。完整数据只允许由受限服务处理；业务库保存供应商凭证、不可逆摘要和最少必要结论。

## 金额模型

订单金额必须由服务端计算并返回签名报价，前端不得提交最终应付金额：

```json
{
  "quoteId": "qt_xxx",
  "serviceAmount": 56400,
  "customerPlatformFee": 0,
  "protectionFee": 2800,
  "payableAmount": 56400,
  "currency": "CNY",
  "expiresAt": "2026-11-10T12:10:00+08:00"
}
```

金额单位统一使用分，禁止浮点数。V1用户侧平台抽佣为0；如果页面展示保障服务费，需明确对应服务和退款规则。B端平台服务费在结算账单中扣除。

用户通过微信支付付款；履约完成并满足结算条件后，平台按微信支付商户侧的合规出款流程结算至Coser微信账户。产品不设置站内钱包、余额或提现入口。退款原路退回，赔付与保证金扣减应有独立账单和服务端记录。前端演示版仅模拟状态，不发起真实资金交易。
