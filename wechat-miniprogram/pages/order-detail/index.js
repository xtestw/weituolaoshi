const { get, set } = require('../../utils/store')
const { createPayment, request } = require('../../services/api')
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
    const coserGender = this.data.order.coserGender || 'F'
    const identityPassed = !!(verification && verification.adult && verification.gender === coserGender)
    this.setData({ identityPassed, identityText: identityPassed ? `已成年 · ${verification.gender === 'F' ? '女' : '男'} · 同性校验通过` : '', verifiedIdentity: !!(verification && verification.verified), showIdentityCard: !identityPassed })
    if (wx.getStorageSync('account_token')) request('/v1/identity/me').then(result => {
      const passed = result.status === 'verified' && result.adult
      if (passed) set('verification', { verified: true, adult: true, gender: result.gender, maskedId: result.maskedId })
      this.setData({ identityPassed: passed, verifiedIdentity: result.status === 'verified', showIdentityCard: !passed, identityText: passed ? `已成年 · ${result.gender === 'F' ? '女' : '男'} · 身份核验通过` : '' })
    }).catch(() => {})
  },
  loadOrder(id) {
    const order = get('orders', []).find((item) => item.id === id) || get('orders', [])[0]
    if (!order) return
    const codes = get('checkInCodes', {})
    let code = codes[order.id]
    if (order.status === 'service' && !code && !order.id.startsWith('ord-')) {
      code = { code: String(Math.floor(1000 + Math.random() * 9000)) }
      set('checkInCodes', { ...codes, [order.id]: code })
    }
    this.setData({ order, checkInCode: order.status === 'service' && code ? code.code : '', reviewed: get('orderReviews', []).some((item) => item.orderId === order.id), boundaryText: (order.boundaries || ['不接受肢体接触', '轻挽手臂', '不饮酒']).join(' · ') })
  },
  verifyIdentity() { wx.navigateTo({ url: '/pages/verify/index?returnTo=order' }) },
  pay() {
    if (!this.data.identityPassed) return this.verifyIdentity()
    createPayment(this.data.order.id).catch(err => wx.showModal({ title: '暂无法支付', content: err.message || '支付服务暂不可用', showCancel: false }))
  },
  updateStatus(status, statusText) {
    const orders = get('orders', []).map((item) => item.id === this.data.order.id ? { ...item, status, statusText } : item)
    set('orders', orders)
    this.setData({ order: { ...this.data.order, status, statusText } })
  },
  contact() { wx.navigateTo({ url: '/pages/chat/index?name=%E9%98%BF%E6%A2%A8' }) },
  callEmergencyContact,
  rateOrder() {
    if (this.data.order.id.startsWith('ord-')) return wx.showModal({ title: '暂无法评分', content: '订单评价服务尚未接入。', showCancel: false })
    if (this.data.order.status !== 'completed' || this.data.reviewed) return
    wx.showActionSheet({ itemList: ['5分 · 非常满意', '4分 · 满意', '3分 · 一般', '2分 · 不满意', '1分 · 很不满意'], success: ({ tapIndex }) => {
      const reviews = get('orderReviews', [])
      if (reviews.some((item) => item.orderId === this.data.order.id)) return
      set('orderReviews', [{ orderId: this.data.order.id, coserId: 'mine', score: 5 - tapIndex, createdAt: Date.now() }, ...reviews])
      this.setData({ reviewed: true })
    } })
  },
  cancel() {
    if (this.data.order.id && this.data.order.id.startsWith('ord-')) return wx.showModal({ title: '暂无法取消', content: '取消与退款服务尚未接入，请联系平台客服。', showCancel: false })
    wx.showActionSheet({ itemList: cancelReasons[this.data.role], success: ({ tapIndex }) => {
      const reason = cancelReasons[this.data.role][tapIndex]
      wx.showModal({
        title: '确认申请取消？',
        content: `原因：${reason}\n\n具体退款金额以订单状态、实际支付金额和取消受理时间由平台核算。`,
        confirmText: '提交申请',
        confirmColor: '#c9384f',
        success: ({ confirm }) => {
          if (!confirm) return
          this.updateStatus('cancel_pending', '取消申请处理中')
          wx.showToast({ title: '已提交', icon: 'none' })
        }
      })
    } })
  },
  complete() {
    if (this.data.order.id && this.data.order.id.startsWith('ord-')) return wx.showModal({ title: '暂无法确认', content: '履约确认服务尚未接入。', showCancel: false })
    wx.showModal({ title: '确认履约完成', content: '双方确认完成后，平台会将订单款结算给Coser。', success: ({ confirm }) => confirm && this.updateStatus('completed', '已完成') })
  }
})
