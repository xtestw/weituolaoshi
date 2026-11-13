const { get } = require('../../utils/store')
Page({
  data: { averageScore: '—', scoredOrders: 0, coserIncompleteCount: 0 },
  onShow() {
    const reviews = Array.from(new Map(get('orderReviews', []).filter((item) => item.coserId === 'mine' && Number(item.score) >= 1 && Number(item.score) <= 5).map((item) => [item.orderId, item])).values())
    const cases = get('responsibilityCases', [])
    this.setData({ averageScore: reviews.length ? (reviews.reduce((sum, item) => sum + Number(item.score), 0) / reviews.length).toFixed(1) : '—', scoredOrders: reviews.length, coserIncompleteCount: new Set(cases.filter((item) => item.coserId === 'mine' && item.status === 'approved' && item.decision === 'coser' && item.fulfillmentIncomplete).map((item) => item.orderId)).size })
  },
  openPublish() { wx.switchTab({ url: '/pages/publish/index' }) },
  openIncome() { wx.showModal({ title: '收入与结算', content: '双方确认履约完成且无争议后，平台按规则结算至微信。退款与争议订单暂缓结算。', showCancel: false }) },
  openSafety() { wx.navigateTo({ url: '/pages/safety/index' }) },
  openSupport() { wx.showModal({ title: '申诉与客服', content: '正式上线时接入客服会话和工单系统。', showCancel: false }) }
})
