const { get, set } = require('../../utils/store')
const { services } = require('../../utils/services')

Page({
  data: {
    serviceId: 'svc-1',
    service: services[0],
    avatar: '梨',
    isSampleProfile: true,
    favorite: false,
    scheduleOpen: false,
    selectedDate: '11月16日 周一',
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
    this.setData({ serviceId: service.id, service, avatar: service.coser.slice(-1), isSampleProfile: service.id === 'svc-1', favorite: get('favorites', []).includes(service.id) })
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
    this.setData({ selectedDate: item.full })
  },
  toggleSchedule() { this.setData({ scheduleOpen: !this.data.scheduleOpen }) },
  openChat() { wx.navigateTo({ url: `/pages/chat/index?name=${encodeURIComponent(this.data.service.coser)}` }) },
  book() { wx.navigateTo({ url: `/pages/booking/index?id=${this.data.serviceId}` }) },
  onShareAppMessage() {
    return { title: this.data.service.title, path: `/pages/coser/index?id=${this.data.serviceId}` }
  }
})
