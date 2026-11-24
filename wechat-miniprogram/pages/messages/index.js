const { request } = require('../../services/api')
Page({
  data: { messages: [], loading: false },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 2, hidden: false })
    this.refresh()
  },
  refresh() {
    if (!wx.getStorageSync('account_token')) { this.setData({ messages: [] }); return }
    this.setData({ loading: true })
    request('/v1/messages/conversations').then(rows => { this.setData({ messages: rows.map(row => ({ ...row, avatar: (row.name || '聊').slice(-1), preview: row.preview || '开始聊天', unread: Number(row.unread || 0), time: '', type: 'person' })) }); if (this.getTabBar && this.getTabBar()) this.getTabBar().setData({ unread: rows.reduce((sum, row) => sum + Number(row.unread || 0), 0) }) }).catch(err => wx.showToast({ title: err.message || '加载失败', icon: 'none' })).finally(() => this.setData({ loading: false }))
  },
  open(event) {
    const item = this.data.messages[event.currentTarget.dataset.index]
    wx.navigateTo({ url: `/pages/chat/index?id=${encodeURIComponent(item.id)}&name=${encodeURIComponent(item.name || '聊天')}` })
  }
})
