Page({
  data: { accepted: false },
  openOrders() { wx.navigateTo({ url: '/pages/b-orders/index' }) },
  openCalendar() { wx.navigateTo({ url: '/pages/b-calendar/index' }) },
  openProfile() { wx.navigateTo({ url: '/pages/b-profile/index' }) },
  openPublish() { wx.switchTab({ url: '/pages/publish/index' }) },
  openMessages() { wx.switchTab({ url: '/pages/messages/index' }) },
  switchCustomer() { wx.switchTab({ url: '/pages/home/index' }) },
  openDeposit() { wx.showModal({ title: '履约保证金', content: '已缴纳保证金 ¥500，当前接单权限正常。Coser责任取消时优先用于向用户赔付，保证金不足需补缴后恢复接单。', showCancel: false }) },
  openException() { wx.showActionSheet({ itemList: ['用户未到场', '地点存在安全风险', '用户需求超出边界', '取消责任争议', '其他异常'], success: () => wx.showToast({ title: '已创建安全工单', icon: 'none' }) }) },
  contact() { wx.navigateTo({ url: '/pages/chat/index?name=%E5%B0%8F%E9%B1%BC' }) },
  reject() { wx.showActionSheet({ itemList: ['身体不适无法履约', '档期冲突', '服装或道具异常', '需求超出服务边界', '其他原因'], success: () => wx.showToast({ title: '已提交原因', icon: 'none' }) }) },
  accept() { wx.showModal({ title: '确认接单', content: '确认后双方进入待履约状态，档期将被锁定，帖子核心信息在订单结束前不能修改。', success: ({ confirm }) => confirm && this.setData({ accepted: true }) }) }
})
