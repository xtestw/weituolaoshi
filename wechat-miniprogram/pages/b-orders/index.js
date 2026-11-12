const { get, set } = require('../../utils/store')

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
  data: { filter: 'all', filters: filters(false), accepted: false },
  onLoad(options) {
    const accepted = get('bOrderAccepted', false)
    if (accepted) syncAcceptedOrder()
    this.setData({ accepted, filters: filters(accepted), filter: ['all', 'pending', 'service', 'done'].includes(options.filter) ? options.filter : 'all' })
  },
  select(event) { this.setData({ filter: event.currentTarget.dataset.key }) },
  contact() { wx.navigateTo({ url: '/pages/chat/index?name=%E5%B0%8F%E9%B1%BC' }) },
  contactSecond() { wx.navigateTo({ url: '/pages/chat/index?name=%E6%9E%97%E6%9E%97' }) },
  checkIn() { wx.showModal({ title: '履约签到', content: '正式版本将校验公共场所定位、订单时间和双方动态口令。', showCancel: false }) },
  openException() { wx.showActionSheet({ itemList: ['用户未到场', '地点存在安全风险', '用户需求超出边界', '取消责任争议', '其他异常'], success: () => wx.showToast({ title: '已创建安全工单', icon: 'none' }) }) },
  accept() { set('bOrderAccepted', true); syncAcceptedOrder(); this.setData({ accepted: true, filter: 'service', filters: filters(true) }); wx.showToast({ title: '已移至待履约', icon: 'none' }) },
  reject() { wx.showActionSheet({ itemList: ['身体不适无法履约','档期冲突','服装或道具异常','需求超出服务边界','其他原因'], success: () => wx.showToast({ title: '已提交原因', icon: 'none' }) }) }
})
