const { get, set } = require('../../utils/store')
const { callEmergencyContact } = require('../../utils/emergency')

function filters(accepted) {
  return [{ key: 'all', label: '全部' }, { key: 'pending', label: `待确认 ${accepted ? 0 : 1}` }, { key: 'service', label: `待履约 ${accepted ? 2 : 1}` }, { key: 'done', label: '已完成 1' }]
}

function syncAcceptedOrder() {
  const orders = get('orders', []).map((order) => order.id === 'WT20261110001' && order.status === 'paid_pending_confirm'
    ? { ...order, status: 'service', statusText: '待履约' }
    : order)
  set('orders', orders)
}

Page({
  data: { filter: 'all', filters: filters(false), accepted: false, checkedIn: false, checkInOpen: false, checkInOrderId: '', checkInInput: '', checkInPhoto: '', checkInLocation: null, checkInBusy: false },
  onLoad(options) {
    const accepted = get('bOrderAccepted', false)
    if (accepted) syncAcceptedOrder()
    this.setData({ accepted, checkedIn: get('orders', []).some((item) => item.id === 'WT20261110001' && item.status === 'in_service'), filters: filters(accepted), filter: ['all', 'pending', 'service', 'done'].includes(options.filter) ? options.filter : 'all' })
  },
  select(event) { this.setData({ filter: event.currentTarget.dataset.key }) },
  contact() { wx.navigateTo({ url: '/pages/chat/index?name=%E5%B0%8F%E9%B1%BC' }) },
  contactSecond() { wx.navigateTo({ url: '/pages/chat/index?name=%E6%9E%97%E6%9E%97' }) },
  callEmergencyContact,
  checkIn(event) { this.setData({ checkInOpen: true, checkInOrderId: event.currentTarget.dataset.orderId || 'WT20261110001', checkInInput: '', checkInPhoto: '', checkInLocation: null }) },
  closeCheckIn() { this.setData({ checkInOpen: false }) },
  stopTap() {},
  onCheckInCode(event) { this.setData({ checkInInput: event.detail.value.replace(/\D/g, '').slice(0, 4) }) },
  captureLocation() { wx.getLocation({ type: 'gcj02', success: ({ latitude, longitude }) => this.setData({ checkInLocation: { latitude, longitude, capturedAt: Date.now() } }), fail: () => wx.showToast({ title: '请允许定位后重试', icon: 'none' }) }) },
  chooseCheckInPhoto() { wx.chooseMedia({ count: 1, mediaType: ['image'], sourceType: ['camera'], success: ({ tempFiles }) => { if (tempFiles && tempFiles[0]) this.setData({ checkInPhoto: tempFiles[0].tempFilePath }) } }) },
  submitCheckIn() {
    if (this.data.checkInBusy) return
    const order = get('orders', []).find((item) => item.id === this.data.checkInOrderId)
    if (!order || order.status !== 'service') return wx.showToast({ title: '订单当前不可签到', icon: 'none' })
    if (!/^\d{4}$/.test(this.data.checkInInput)) return wx.showToast({ title: '请输入用户提供的4位码', icon: 'none' })
    if (!this.data.checkInLocation) return wx.showToast({ title: '请先获取当前位置', icon: 'none' })
    if (!this.data.checkInPhoto) return wx.showToast({ title: '请至少上传一张现场照片', icon: 'none' })
    const code = get('checkInCodes', {})[order.id]
    if (!code || code.code !== this.data.checkInInput) return wx.showToast({ title: '履约码错误，请向用户核对', icon: 'none' })
    this.setData({ checkInBusy: true })
    const orders = get('orders', []).map((item) => item.id === order.id ? { ...item, status: 'in_service', statusText: '履约中', checkInAt: Date.now(), checkInLocation: this.data.checkInLocation, checkInPhoto: this.data.checkInPhoto } : item)
    set('orders', orders)
    const codes = get('checkInCodes', {})
    delete codes[order.id]
    set('checkInCodes', codes)
    this.setData({ checkInOpen: false, checkInBusy: false, checkedIn: true })
    wx.showToast({ title: '现场签到成功', icon: 'success' })
  },
  openException() { wx.showActionSheet({ itemList: ['用户未到场', '地点存在安全风险', '用户需求超出边界', '取消责任争议', '其他异常'], success: () => wx.showToast({ title: '已创建安全工单', icon: 'none' }) }) },
  accept() { set('bOrderAccepted', true); syncAcceptedOrder(); this.setData({ accepted: true, filter: 'service', filters: filters(true) }); wx.showToast({ title: '已移至待履约', icon: 'none' }) },
  reject() { wx.showActionSheet({ itemList: ['身体不适无法履约','档期冲突','服装或道具异常','需求超出服务边界','其他原因'], success: ({ tapIndex }) => {
    const reasons = ['身体不适无法履约','档期冲突','服装或道具异常','需求超出服务边界','其他原因']
    set('responsibilityCases', [{ id: `case-${Date.now()}`, coserId: 'mine', orderId: 'WT20261110001', reason: reasons[tapIndex], status: 'pending', decision: null, fulfillmentIncomplete: this.data.accepted, submittedAt: Date.now() }, ...get('responsibilityCases', [])])
    wx.showModal({ title: '已提交平台审核', content: '平台审核确认责任后更新订单状态；仅确认属于Coser责任的未完成履约订单计数。', showCancel: false })
  } }) }
})
