const { get, set } = require('../../utils/store')
const { services } = require('../../utils/services')
const { nextDays } = require('../../utils/availability')
const { guardBooking } = require('../../utils/eligibility')

Page({
  data: {
    serviceId: 'svc-1',
    service: services[0],
    avatar: '梨',
    isSampleProfile: true,
    favorite: false,
    scheduleOpen: false,
    selectedDate: '',
    selectedDateKey: '',
    dates: [],
    reputation: { score: '—', scoredOrders: 0, incompleteCount: 0, sample: false },
    boundaries: ['不接受肢体接触', '轻挽手臂', '不饮酒'],
    clothes: [
      { name: '三月七·巡猎', detail: '全套 · 含假发道具' },
      { name: '三月七·常服', detail: '全套 · 含假发' },
      { name: '芙宁娜', detail: '全套 · 含帽子道具' },
      { name: '流萤', detail: '全套 · 含假发配饰' }
    ]
  },
  onLoad(options) {
    const serviceId = options.id || 'svc-1'
    const service = services.find((item) => item.id === serviceId) || services[0]
    const dates = nextDays(service)
    const firstOpen = dates.find((item) => item.open)
    this.setData({ serviceId: service.id, service, dates, selectedDate: firstOpen ? firstOpen.full : '暂无可约日期', selectedDateKey: firstOpen ? firstOpen.key : '', avatar: service.coser.slice(-1), isSampleProfile: service.id === 'svc-1', favorite: get('favorites', []).includes(service.id) })
    this.refreshReputation()
  },
  onShow() { if (this.data.serviceId) this.refreshReputation() },
  refreshReputation() {
    const service = this.data.service
    const coserId = service.id === 'svc-1' ? 'mine' : service.id
    const reviews = Array.from(new Map(get('orderReviews', []).filter((item) => item.coserId === coserId && Number(item.score) >= 1 && Number(item.score) <= 5).map((item) => [item.orderId, item])).values())
    const cases = get('responsibilityCases', [])
    const incompleteCount = new Set(cases.filter((item) => item.coserId === coserId && item.status === 'approved' && item.decision === 'coser' && item.fulfillmentIncomplete).map((item) => item.orderId)).size
    this.setData({ reputation: { score: reviews.length ? (reviews.reduce((sum, item) => sum + Number(item.score), 0) / reviews.length).toFixed(1) : service.rating ? Number(service.rating).toFixed(1) : '—', scoredOrders: reviews.length, incompleteCount, sample: !reviews.length && !!service.rating } })
  },
  toggleFavorite() {
    const favorites = get('favorites', [])
    const next = favorites.includes(this.data.serviceId) ? favorites.filter((id) => id !== this.data.serviceId) : [...favorites, this.data.serviceId]
    set('favorites', next)
    this.setData({ favorite: next.includes(this.data.serviceId) })
    wx.showToast({ title: this.data.favorite ? '已收藏' : '已取消', icon: 'none' })
  },
  selectDate(event) {
    const item = this.data.dates[event.currentTarget.dataset.index]
    if (!item.open) return wx.showToast({ title: '该日期不可约', icon: 'none' })
    this.setData({ selectedDate: item.full, selectedDateKey: item.key })
  },
  toggleSchedule() { this.setData({ scheduleOpen: !this.data.scheduleOpen }) },
  openChat() { wx.navigateTo({ url: `/pages/chat/index?name=${encodeURIComponent(this.data.service.coser)}` }) },
  book() { if (guardBooking()) wx.navigateTo({ url: `/pages/booking/index?id=${this.data.serviceId}&date=${this.data.selectedDateKey}` }) },
  onShareAppMessage() {
    return { title: this.data.service.title, path: `/pages/coser/index?id=${this.data.serviceId}` }
  }
})
