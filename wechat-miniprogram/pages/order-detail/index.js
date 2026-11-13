const { get, set } = require('../../utils/store')
const { bookingEligibility, guardBooking } = require('../../utils/eligibility')
const { callEmergencyContact } = require('../../utils/emergency')

const cancelReasons = {
  customer: ['行程计划有变', '身体不适无法到场', '与Coser协商一致', '时间或地点需要调整', '误操作下单', '其他原因'],
  coser: ['身体不适无法履约', '档期冲突', '服装或道具异常', '公共场所临时关闭', '用户需求超出服务边界', '与用户协商一致', '其他原因']
}

Page({
  data: { order: {}, isNew: false, role: 'customer', showIdentityCard: false, identityPassed: false, identityText: '', checkInCode: '', reviewed: false, boundaryText: '不接受肢体接触 · 轻挽手臂 · 不饮酒' },
  onLoad(options) { this.setData({ isNew: options.new === '1', showIdentityCard: !get('verification', null) }); this.loadOrder(options.id) },
  onShow() {
    if (this.data.order.id) this.loadOrder(this.data.order.id)
    const verification = get('verification', null)
    const identityPassed = bookingEligibility().allowed
    this.setData({ identityPassed, identityText: identityPassed ? '已成年 · 女性身份核验通过' : '', verifiedIdentity: !!(verification && verification.verified), showIdentityCard: !identityPassed })
  },
  loadOrder(id) {
    const order = get('orders', []).find((item) => item.id === id) || get('orders', [])[0]
    if (!order) return
    const codes = get('checkInCodes', {})
    let code = codes[order.id]
    if (order.status === 'service' && !code) {
      code = { code: String(Math.floor(1000 + Math.random() * 9000)) }
      set('checkInCodes', { ...codes, [order.id]: code })
    }
    this.setData({ order, checkInCode: order.status === 'service' && code ? code.code : '', reviewed: get('orderReviews', []).some((item) => item.orderId === order.id), boundaryText: (order.boundaries || ['不接受肢体接触', '轻挽手臂', '不饮酒']).join(' · ') })
  },
  verifyIdentity() { wx.navigateTo({ url: '/pages/verify/index?returnTo=order' }) },
  pay() {
    if (!guardBooking()) return
    wx.showModal({ title: '演示支付', content: '当前为前端演示环境。配置微信支付商户号和后端签名接口后，此处会调起微信支付。', confirmText: '模拟支付', success: ({ confirm }) => {
      if (!confirm) return
      this.updateStatus('paid_pending_confirm', '已付款，待Coser确认')
      wx.showToast({ title: '支付成功' })
    } })
  },
  updateStatus(status, statusText) {
    const orders = get('orders', []).map((item) => item.id === this.data.order.id ? { ...item, status, statusText } : item)
    set('orders', orders)
    this.setData({ order: { ...this.data.order, status, statusText } })
  },
  contact() { wx.navigateTo({ url: '/pages/chat/index?name=%E9%98%BF%E6%A2%A8' }) },
  callEmergencyContact,
  rateOrder() {
    if (this.data.order.status !== 'completed' || this.data.reviewed) return
    wx.showActionSheet({ itemList: ['5分 · 非常满意', '4分 · 满意', '3分 · 一般', '2分 · 不满意', '1分 · 很不满意'], success: ({ tapIndex }) => {
      const reviews = get('orderReviews', [])
      if (reviews.some((item) => item.orderId === this.data.order.id)) return
      set('orderReviews', [{ orderId: this.data.order.id, coserId: 'mine', score: 5 - tapIndex, createdAt: Date.now() }, ...reviews])
      this.setData({ reviewed: true })
      wx.showToast({ title: '评分已提交', icon: 'none' })
    } })
  },
  cancel() {
    wx.showActionSheet({ itemList: cancelReasons[this.data.role], success: ({ tapIndex }) => {
      const reason = cancelReasons[this.data.role][tapIndex]
      wx.showModal({
        title: '确认申请取消？',
        content: `原因：${reason}\n\n48小时外可无责取消；24–48小时内对方最长12小时确认；24小时内需先通过聊天协商。未达成一致时由平台判定责任并按规则赔付。`,
        confirmText: '提交申请',
        confirmColor: '#c9384f',
        success: ({ confirm }) => {
          if (!confirm) return
          const cases = get('responsibilityCases', [])
          set('responsibilityCases', [{ id: `case-${Date.now()}`, coserId: this.data.order.coserId || 'mine', orderId: this.data.order.id, reason, status: 'pending', decision: null, fulfillmentIncomplete: this.data.order.status === 'service', submittedAt: Date.now() }, ...cases])
          this.updateStatus('cancel_pending', '取消申请待平台审核')
          wx.showToast({ title: '已提交', icon: 'none' })
        }
      })
    } })
  },
  complete() {
    if (this.data.order.status !== 'in_service') return wx.showToast({ title: '请先完成现场签到', icon: 'none' })
    wx.showModal({ title: '确认履约完成', content: '双方确认完成后，平台会将订单款结算给Coser。', success: ({ confirm }) => confirm && this.updateStatus('completed', '已完成') })
  }
})
