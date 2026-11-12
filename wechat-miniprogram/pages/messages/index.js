Page({
  data: {
    messages: [
      { id: 'chat-ali', name: '阿梨', avatar: '梨', preview: '11月16日的档期可以直接确认哦', time: '刚刚', unread: 2, type: 'person' },
      { id: 'system', name: '平台通知', avatar: '平', preview: '您的实名认证信息已通过审核', time: '10:24', unread: 0, type: 'system' },
      { id: 'order', name: '订单助手', avatar: '单', preview: '委托已付款，等待Coser确认', time: '昨天', unread: 0, type: 'order' }
    ]
  },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 2, hidden: false })
  },
  open(event) {
    const item = this.data.messages[event.currentTarget.dataset.index]
    if (item.type === 'person') wx.navigateTo({ url: `/pages/chat/index?name=${encodeURIComponent(item.name)}` })
    else wx.showModal({ title: item.name, content: item.preview, showCancel: false })
  }
})
