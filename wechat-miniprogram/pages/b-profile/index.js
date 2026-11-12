Page({
  openPublish() { wx.switchTab({ url: '/pages/publish/index' }) },
  openIncome() { wx.showModal({ title: '收入与结算', content: '双方确认履约完成且无争议后，平台按规则结算至微信。退款与争议订单暂缓结算。', showCancel: false }) },
  openDeposit() { wx.showModal({ title: '平台保证金', content: '已缴纳 ¥500。发生Coser责任取消并判定需赔付时，平台会按规则从保证金扣除并生成账单。', showCancel: false }) },
  openSafety() { wx.navigateTo({ url: '/pages/safety/index' }) },
  openSupport() { wx.showModal({ title: '申诉与客服', content: '正式上线时接入客服会话和工单系统。', showCancel: false }) }
})
