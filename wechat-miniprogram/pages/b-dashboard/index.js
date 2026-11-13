const { get, set } = require('../../utils/store')
const { callEmergencyContact } = require('../../utils/emergency')
Page({
  data: { accepted: false, coserIncompleteCount: 0, averageScore: '—', scoredOrders: 0 },
  onShow() {
    const cases = get('responsibilityCases', [])
    const reviews = get('orderReviews', []).filter((item) => item.coserId === 'mine' && Number(item.score) >= 1 && Number(item.score) <= 5)
    const uniqueReviews = Array.from(new Map(reviews.map((item) => [item.orderId, item])).values())
    const averageScore = uniqueReviews.length ? (uniqueReviews.reduce((sum, item) => sum + Number(item.score), 0) / uniqueReviews.length).toFixed(1) : '—'
    this.setData({ coserIncompleteCount: new Set(cases.filter((item) => item.status === 'approved' && item.decision === 'coser' && item.fulfillmentIncomplete && item.coserId === 'mine').map((item) => item.orderId)).size, averageScore, scoredOrders: uniqueReviews.length, accepted: get('bOrderAccepted', false) })
  },
  openOrders() { wx.navigateTo({ url: '/pages/b-orders/index' }) },
  openCalendar() { wx.navigateTo({ url: '/pages/b-calendar/index' }) },
  openProfile() { wx.navigateTo({ url: '/pages/b-profile/index' }) },
  openPublish() { wx.switchTab({ url: '/pages/publish/index' }) },
  openMessages() { wx.switchTab({ url: '/pages/messages/index' }) },
  switchCustomer() { wx.switchTab({ url: '/pages/home/index' }) },
  callEmergencyContact,
  openException() { wx.showActionSheet({ itemList: ['用户未到场', '地点存在安全风险', '用户需求超出边界', '取消责任争议', '其他异常'], success: () => wx.showToast({ title: '已创建安全工单', icon: 'none' }) }) },
  contact() { wx.navigateTo({ url: '/pages/chat/index?name=%E5%B0%8F%E9%B1%BC' }) },
  reject() { wx.showActionSheet({ itemList: ['身体不适无法履约', '档期冲突', '服装或道具异常', '需求超出服务边界', '其他原因'], success: ({ tapIndex }) => {
    const reasons = ['身体不适无法履约', '档期冲突', '服装或道具异常', '需求超出服务边界', '其他原因']
    const cases = get('responsibilityCases', [])
    set('responsibilityCases', [{ id: `case-${Date.now()}`, coserId: 'mine', orderId: 'WT20261110001', reason: reasons[tapIndex], status: 'pending', decision: null, fulfillmentIncomplete: this.data.accepted, submittedAt: Date.now() }, ...cases])
    wx.showModal({ title: '已提交平台审核', content: '原因已记录。只有平台审核确认属于Coser责任且未完成履约后，才会计入首页次数。', showCancel: false })
  } }) },
  accept() { wx.showModal({ title: '确认接单', content: '确认后双方进入待履约状态，档期将被锁定，帖子核心信息在订单结束前不能修改。', success: ({ confirm }) => confirm && this.setData({ accepted: true }) }) }
})
