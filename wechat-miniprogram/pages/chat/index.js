const { request } = require('../../services/api')
const base = () => getApp().globalData.apiBaseUrl
Page({
  data: { name: '聊天', id: '', text: '', messages: [], startAt: '', endAt: '', isCoser: false, loading: false, hasMore: false },
  onLoad(options) {
    this.setData({ name: decodeURIComponent(options.name || '聊天'), isCoser: wx.getStorageSync('account_user')?.role === 'coser' })
    wx.setNavigationBarTitle({ title: this.data.name })
    if (options.id) { this.setData({ id: decodeURIComponent(options.id) }); this.load() }
    else if (options.serviceId || options.orderId) {
      request('/v1/messages/conversations', { method: 'POST', data: options.orderId ? { orderId: decodeURIComponent(options.orderId) } : { serviceId: decodeURIComponent(options.serviceId) } })
        .then(({ id }) => { this.setData({ id }); this.load(); this.connect() })
        .catch(err => wx.showModal({ title: '无法开始会话', content: err.message || '请先登录', showCancel: false }))
    } else wx.showModal({ title: '无法开始会话', content: '缺少真实服务或订单信息', showCancel: false })
  },
  onShow() { this.active = true; if (this.data.id) { this.load(); this.connect() } },
  onHide() { this.active = false; this.closeSocket() }, onUnload() { this.active = false; this.closeSocket() },
  closeSocket() { clearTimeout(this.reconnectTimer); if (this.socket) { const socket = this.socket; this.socket = null; socket.close() } },
  connect() {
    if (!this.active || this.socket || !wx.getStorageSync('account_token') || !base()) return
    const url = `${base().replace(/^http/, 'ws')}/v1/messages/socket`
    const socket = wx.connectSocket({ url })
    this.socket = socket
    socket.onOpen(() => socket.send({ data: JSON.stringify({ type: 'auth', token: wx.getStorageSync('account_token') }) }))
    socket.onMessage(({ data }) => { try { const event = JSON.parse(data); if (event.type === 'conversation.updated' && event.conversationId === this.data.id) this.load() } catch (_) {} })
    socket.onClose(() => { if (this.socket === socket) { this.socket = null; clearTimeout(this.reconnectTimer); this.reconnectTimer = setTimeout(() => this.connect(), 2000) } })
  },
  load() {
    if (!this.data.id || this.data.loading) return
    this.setData({ loading: true })
    request(`/v1/messages/conversations/${encodeURIComponent(this.data.id)}/messages`).then(rows => {
      const me = wx.getStorageSync('account_user')?.id
      this.setData({ messages: this.formatMessages(rows.reverse(), me), hasMore: rows.length === 50 })
      return request(`/v1/messages/conversations/${encodeURIComponent(this.data.id)}/read`, { method: 'POST' })
    }).catch(err => wx.showToast({ title: err.message || '加载失败', icon: 'none' })).finally(() => this.setData({ loading: false }))
  },
  formatMessages(rows, me) { return rows.map(item => ({ ...item, from: item.senderId === me ? 'me' : 'other', text: item.type === 'image' ? (item.status === 'approved' ? '[图片，点击查看]' : '[图片审核中]') : item.status === 'pending' ? `${item.body}（审核中，仅自己可见）` : item.status === 'rejected' ? `${item.body}（未通过审核）` : item.body })) },
  loadOlder() {
    if (!this.data.hasMore || !this.data.messages.length) return
    const before = String(this.data.messages[0].createdAt).replace(' ', 'T')
    request(`/v1/messages/conversations/${encodeURIComponent(this.data.id)}/messages?before=${encodeURIComponent(before)}`).then(rows => this.setData({ messages: [...this.formatMessages(rows.reverse(), wx.getStorageSync('account_user')?.id), ...this.data.messages], hasMore: rows.length === 50 }))
  },
  onText(event) { this.setData({ text: event.detail.value }) },
  send() {
    const text = this.data.text.trim(); if (!text || !this.data.id) return
    request(`/v1/messages/conversations/${encodeURIComponent(this.data.id)}/messages`, { method: 'POST', data: { body: text, clientId: `wx-${Date.now()}-${Math.random().toString(36).slice(2)}` } })
      .then(() => { this.setData({ text: '' }); this.load() })
      .catch(err => wx.showToast({ title: err.message || '发送失败', icon: 'none' }))
  },
  chooseImage() {
    if (!this.data.id) return
    wx.chooseMedia({ count: 1, mediaType: ['image'], success: ({ tempFiles }) => {
      wx.uploadFile({ url: `${base()}/v1/messages/conversations/${encodeURIComponent(this.data.id)}/images`, filePath: tempFiles[0].tempFilePath, name: 'file', header: { Authorization: `Bearer ${wx.getStorageSync('account_token')}` }, success: ({ statusCode, data }) => {
        if (statusCode >= 200 && statusCode < 300) this.load()
        else { let message = '上传失败'; try { message = JSON.parse(data).message || message } catch (_) {} wx.showToast({ title: message, icon: 'none' }) }
      }, fail: () => wx.showToast({ title: '上传失败', icon: 'none' }) })
    } })
  },
  viewImage(event) {
    const item = this.data.messages[event.currentTarget.dataset.index]
    if (item.type !== 'image' || item.status !== 'approved') return
    wx.downloadFile({ url: `${base()}/v1/messages/conversations/${encodeURIComponent(this.data.id)}/images/${item.id}`, header: { Authorization: `Bearer ${wx.getStorageSync('account_token')}` }, success: ({ statusCode, tempFilePath }) => { if (statusCode === 200) wx.previewImage({ urls: [tempFilePath] }) } })
  },
  report(event) {
    const item = this.data.messages[event.currentTarget.dataset.index]
    if (item.from === 'me') return
    wx.showActionSheet({ itemList: ['站外交易或导流', '骚扰或不当内容', '其他'], success: ({ tapIndex }) => request(`/v1/messages/messages/${item.id}/reports`, { method: 'POST', data: { reason: ['站外交易或导流', '骚扰或不当内容', '其他'][tapIndex] } }).then(() => wx.showToast({ title: '举报已提交' })).catch(err => wx.showToast({ title: err.message, icon: 'none' })) })
  },
  onStart(event) { this.setData({ startAt: event.detail.value }) }, onEnd(event) { this.setData({ endAt: event.detail.value }) },
  confirmTime() {
    if (!this.data.startAt || !this.data.endAt) return wx.showToast({ title: '请选择开始和结束时间', icon: 'none' })
    request(`/v1/messages/conversations/${encodeURIComponent(this.data.id)}/confirm-time`, { method: 'POST', data: { startAt: this.data.startAt.replace(' ', 'T') + ':00', endAt: this.data.endAt.replace(' ', 'T') + ':00' } })
      .then(() => wx.showToast({ title: '时间已确认' })).catch(err => wx.showToast({ title: err.message, icon: 'none' }))
  }
})
