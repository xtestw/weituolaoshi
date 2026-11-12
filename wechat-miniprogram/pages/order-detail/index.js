const { get, set } = require('../../utils/store')

const cancelReasons = {
  customer: ['行程计划有变', '身体不适无法到场', '与Coser协商一致', '时间或地点需要调整', '误操作下单', '其他原因'],
  coser: ['身体不适无法履约', '档期冲突', '服装或道具异常', '公共场所临时关闭', '用户需求超出服务边界', '与用户协商一致', '其他原因']
}

Page({
  data: { order: {}, isNew: false, role: 'customer', showIdentityCard: false, identityPassed: false, identityText: '', boundaryText: '不接受肢体接触 · 轻挽手臂 · 不饮酒' },
  onLoad(options) { this.setData({ isNew: options.new === '1', showIdentityCard: !get('verification', null) }); this.loadOrder(options.id) },
  onShow() {
    if (this.data.order.id) this.loadOrder(this.data.order.id)
    const verification = get('verification', null)
    const coserGender = this.data.order.coserGender || 'F'
    const identityPassed = !!(verification && verification.adult && verification.gender === coserGender)
    this.setData({ identityPassed, identityText: identityPassed ? `已成年 · ${verification.gender === 'F' ? '女' : '男'} · 同性校验通过` : '' })
  },
  loadOrder(id) {
    const order = get('orders', []).find((item) => item.id === id) || get('orders', [])[0]
    if (!order) return
    this.setData({ order, boundaryText: (order.boundaries || ['不接受肢体接触', '轻挽手臂', '不饮酒']).join(' · ') })
  },
  verifyIdentity() { wx.navigateTo({ url: '/pages/verify/index?returnTo=order' }) },
  pay() {
    if (!this.data.identityPassed) return this.verifyIdentity()
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
          this.updateStatus('cancel_pending', '取消申请处理中')
          wx.showToast({ title: '已提交', icon: 'none' })
        }
      })
    } })
  },
  complete() {
    wx.showModal({ title: '确认履约完成', content: '双方确认完成后，平台会将订单款结算给Coser。', success: ({ confirm }) => confirm && this.updateStatus('completed', '已完成') })
  }
})
