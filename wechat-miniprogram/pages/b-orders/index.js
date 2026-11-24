const { request } = require('../../services/api')
const { normalizeOrder } = require('../../utils/order-api')
const { callEmergencyContact } = require('../../utils/emergency')

Page({
  data: { filter: 'all', filters: [{ key:'all', label:'全部' }, { key:'pending', label:'待确认' }, { key:'service', label:'待履约' }, { key:'done', label:'已完成' }],
    orders: [], visibleOrders: [], checkInOpen: false, checkInOrderId: '', checkInInput: '', checkInPhoto: '', checkInLocation: null, checkInBusy: false },
  onLoad(options) { this.setData({ filter: ['all','pending','service','done'].includes(options.filter) ? options.filter : 'all' }) },
  onShow() { this.refresh() },
  refresh() {
    if (!wx.getStorageSync('account_token')) return
    request('/v1/coser/orders').then(rows => this.setData({ orders: rows.map(normalizeOrder) }, this.applyFilter))
      .catch(err => wx.showToast({ title: err.message || '订单加载失败', icon: 'none' }))
  },
  applyFilter() {
    const states = { pending: ['paid_pending_confirm'], service: ['service','in_service'], done: ['completed','cancelled','cancel_pending'] }
    this.setData({ visibleOrders: this.data.filter === 'all' ? this.data.orders : this.data.orders.filter(item => states[this.data.filter].includes(item.status)) })
  },
  select(event) { this.setData({ filter: event.currentTarget.dataset.key }, this.applyFilter) },
  contact(event) { wx.navigateTo({ url: '/pages/chat/index?orderId=' + encodeURIComponent(event.currentTarget.dataset.id) }) },
  callEmergencyContact,
  action(path, id, data) {
    return request('/v1/orders/' + encodeURIComponent(id) + '/' + path, { method: 'POST', data })
      .then(() => this.refresh()).catch(err => wx.showToast({ title: err.message || '操作失败', icon: 'none' }))
  },
  accept(event) { this.action('confirm', event.currentTarget.dataset.id) },
  reject(event) {
    const id=event.currentTarget.dataset.id
    const reasons=['身体不适无法履约','档期冲突','服装或道具异常','需求超出服务边界','其他原因']
    wx.showActionSheet({ itemList: reasons, success: ({ tapIndex }) => this.action('reject',id,{ reason: reasons[tapIndex] }) })
  },
  complete(event) { this.action('complete-confirmations',event.currentTarget.dataset.id) },
  openException(event) {
    const id=event.currentTarget.dataset.id
    const reasons=['用户未到场','地点存在安全风险','用户需求超出边界','取消责任争议','其他异常']
    wx.showActionSheet({ itemList: reasons, success: ({ tapIndex }) => this.action('exceptions',id,{ reason: reasons[tapIndex] }) })
  },
  checkIn(event) { this.setData({ checkInOpen: true, checkInOrderId: event.currentTarget.dataset.id, checkInInput: '', checkInPhoto: '', checkInLocation: null }) },
  closeCheckIn() { this.setData({ checkInOpen: false }) },
  stopTap() {},
  onCheckInCode(event) { this.setData({ checkInInput: event.detail.value.replace(/\D/g, '').slice(0, 4) }) },
  captureLocation() { wx.getLocation({ type: 'gcj02', success: ({ latitude, longitude }) => this.setData({ checkInLocation: { latitude, longitude } }), fail: () => wx.showToast({ title: '请允许定位后重试', icon: 'none' }) }) },
  chooseCheckInPhoto() { wx.chooseMedia({ count: 1, mediaType: ['image'], sourceType: ['camera'], success: ({ tempFiles }) => { if (tempFiles && tempFiles[0]) this.setData({ checkInPhoto: tempFiles[0].tempFilePath }) } }) },
  submitCheckIn() {
    if (this.data.checkInBusy) return
    if (!/^\d{4}$/.test(this.data.checkInInput) || !this.data.checkInLocation || !this.data.checkInPhoto)
      return wx.showToast({ title: '请填写履约码、定位并拍照', icon: 'none' })
    const { checkInOrderId: id, checkInInput: code, checkInLocation: location, checkInPhoto: photo } = this.data
    this.setData({ checkInBusy: true })
    wx.uploadFile({ url: getApp().globalData.apiBaseUrl + '/v1/orders/' + encodeURIComponent(id) + '/check-in', filePath: photo,
      name: 'photo', formData: { code, latitude: String(location.latitude), longitude: String(location.longitude) },
      header: { Authorization: 'Bearer ' + wx.getStorageSync('account_token') },
      success: ({ statusCode, data }) => {
        if (statusCode >= 200 && statusCode < 300) { this.setData({ checkInOpen: false }); this.refresh(); wx.showToast({ title: '签到成功', icon: 'success' }) }
        else { let message='签到失败'; try { message=JSON.parse(data).message || message } catch (_) {} wx.showToast({ title: message, icon: 'none' }) }
      }, fail: () => wx.showToast({ title: '上传失败', icon: 'none' }), complete: () => this.setData({ checkInBusy: false }) })
  }
})
