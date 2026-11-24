const { get } = require('../../utils/store')
const { loadOrders } = require('../../utils/order-api')
Page({
  data: { tab: 'all', tabs: [{key:'all',label:'全部'},{key:'confirm',label:'待确认'},{key:'pay',label:'待支付'},{key:'service',label:'待履约'},{key:'review',label:'待评价'}], orders: [], visibleOrders: [] },
  onShow() {
    const orders = get('orders', []); this.setData({ orders }, this.applyFilter)
    if (wx.getStorageSync('account_token')) loadOrders().then(rows => this.setData({ orders: rows }, this.applyFilter))
      .catch(err => wx.showToast({ title: err.message || '订单加载失败', icon: 'none' }))
  },
  select(event) { this.setData({ tab: event.currentTarget.dataset.key }, this.applyFilter) },
  applyFilter() {
    const statusMap = { confirm: ['paid_pending_confirm'], pay: ['unpaid'], service: ['service'], review: ['completed'] }
    const visibleOrders = this.data.tab === 'all' ? this.data.orders : this.data.orders.filter((item) => (statusMap[this.data.tab] || []).includes(item.status))
    this.setData({ visibleOrders })
  },
  open(event) { wx.navigateTo({ url: `/pages/order-detail/index?id=${event.currentTarget.dataset.id}` }) }
})
