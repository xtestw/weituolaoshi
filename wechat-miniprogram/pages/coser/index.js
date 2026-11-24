const { get, set } = require('../../utils/store')
const { services, mapService } = require('../../utils/services')
const { request } = require('../../services/api')
const { guardBooking } = require('../../utils/eligibility')

Page({
  data: {
    serviceId: 'svc-1',
    service: services[0],
    avatar: '梨',
    isSampleProfile: true,
    favorite: false,
    media: [],
    scheduleOpen: false,
    selectedDate: '11月16日 周一',
    selectedDateKey: '',
    reputation: { score: '—', scoredOrders: 0, incompleteCount: 0, sample: false },
    dates: [
      { label: '11/16', sub: '周一', full: '11月16日 周一', open: true },
      { label: '11/17', sub: '周二', full: '11月17日 周二', open: true },
      { label: '11/18', sub: '周三', full: '11月18日 周三', open: false },
      { label: '11/21', sub: '周六', full: '11月21日 周六', open: true }
    ],
    hours: Array.from({ length: 24 }, (_, hour) => ({ label: String(hour).padStart(2, '0'), open: (hour >= 10 && hour < 12) || (hour >= 14 && hour < 18) || (hour >= 19 && hour < 22) })),
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
    this.setData({ serviceId, service, avatar: service.coser.slice(-1), isSampleProfile: serviceId === 'svc-1', favorite: get('favorites', []).includes(serviceId) })
    this.refreshReputation()
    request(`/v1/services/${encodeURIComponent(serviceId)}`).then(detail => {
      const live = { ...mapService(detail), description: detail.description || '', serviceTerms: detail.serviceTerms || '', minHours: detail.minHours }
      this.setData({ service: live, avatar: live.coser.slice(-1), isSampleProfile: false, boundaries: detail.boundaries || [], media: (detail.media || []).map(item => ({ ...item, url: item.url && item.url.startsWith('/') ? `${getApp().globalData.apiBaseUrl}${item.url}` : item.url })) })
    }).catch(() => {})
    request(`/v1/cosers/${encodeURIComponent(serviceId)}/availability`).then(({ slots }) => {
      const days = []
      slots.forEach(slot => {
        const date = String(slot.startAt).slice(0, 10)
        if (!days.some(item => item.date === date)) days.push({ date, key: date, label: date.slice(5).replace('-', '/'), sub: '', full: date, open: true })
      })
      this.setData({ dates: days.slice(0, 14), selectedDate: days.length ? days[0].full : '暂无可约日期', selectedDateKey: days.length ? days[0].key : '' })
    }).catch(() => {})
  },
  onShow() { if (this.data.serviceId) this.refreshReputation() },
  refreshReputation() {
    const service = this.data.service
    const coserId = service.id === 'svc-1' ? 'mine' : service.id
    const reviews = Array.from(new Map(get('orderReviews', []).filter(item => item.coserId === coserId && Number(item.score) >= 1 && Number(item.score) <= 5).map(item => [item.orderId, item])).values())
    const cases = get('responsibilityCases', [])
    const incompleteCount = new Set(cases.filter(item => item.coserId === coserId && item.status === 'approved' && item.decision === 'coser' && item.fulfillmentIncomplete).map(item => item.orderId)).size
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
    if (!item.open) return wx.showToast({ title: '当天暂无可约时间', icon: 'none' })
    this.setData({ selectedDate: item.full, selectedDateKey: item.key })
  },
  toggleSchedule() { this.setData({ scheduleOpen: !this.data.scheduleOpen }) },
  openChat() { wx.navigateTo({ url: `/pages/chat/index?serviceId=${encodeURIComponent(this.data.serviceId)}&name=${encodeURIComponent(this.data.service.coser)}` }) },
  book() { if (!this.data.dates.length) return wx.showToast({ title: '暂无可约时间', icon: 'none' }); if (!getApp().globalData.localDevMode && !guardBooking()) return; wx.navigateTo({ url: `/pages/booking/index?id=${this.data.serviceId}` }) },
  onShareAppMessage() {
    return { title: this.data.service.title, path: `/pages/coser/index?id=${this.data.serviceId}` }
  }
})
