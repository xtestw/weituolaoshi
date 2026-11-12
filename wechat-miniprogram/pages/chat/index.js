Page({
  data: {
    name: '阿梨',
    text: '',
    confirmed: false,
    messages: [
      { id: 'm1', from: 'other', text: '你好呀，11月16日14:00到17:00可以直接预约。' },
      { id: 'm2', from: 'me', text: '好的，见面地点选国家会展中心2号门。' }
    ]
  },
  onLoad(options) { this.setData({ name: decodeURIComponent(options.name || '阿梨') }); wx.setNavigationBarTitle({ title: this.data.name }) },
  onText(event) { this.setData({ text: event.detail.value }) },
  send() {
    const text = this.data.text.trim()
    if (!text) return
    this.setData({ messages: [...this.data.messages, { id: `m${Date.now()}`, from: 'me', text }], text: '' })
  },
  confirmTime() {
    this.setData({ confirmed: true, messages: [...this.data.messages, { id: `m${Date.now()}`, from: 'system', text: '双方已确认：11月16日 14:00–17:00' }] })
    wx.showToast({ title: '时间已确认' })
  }
})
