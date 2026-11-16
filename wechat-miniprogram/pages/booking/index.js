const { get, set } = require('../../utils/store')
const { services, mapService } = require('../../utils/services')
const { request } = require('../../services/api')
const { guardBooking } = require('../../utils/eligibility')

const buildHours = (startTime, duration, date, slots) => {
  const start = Number(String(startTime).split(':')[0])
  return Array.from({ length: 24 }, (_, hour) => ({
    label: String(hour).padStart(2, '0'),
    open: slots.some(slot => slot.startAt <= `${date}T${String(hour).padStart(2, '0')}:00` && slot.endAt >= `${date}T${String(hour + 1).padStart(2, '0')}:00`),
    selected: hour >= start && hour < start + duration
  }))
}

Page({
  data: {
    service: services[0],
    date: '',
    bookingDates: [],
    slots: [],
    venues: [],
    startTime: '14:00',
    duration: 3,
    hours: buildHours('14:00', 3, '', []),
    isAvailable: false,
    place: '',
    placeDetails: null,
    note: '',
    nickname: '',
    likes: '',
    dislikes: '',
    contactBoundary: '不接受肢体接触',
    boundaryOptions: ['不接受肢体接触', '可轻挽手臂', '仅接受合影姿势接触'],
    boundaries: [
      { text: '不接受肢体接触', confirmed: false },
      { text: '轻挽手臂', confirmed: false },
      { text: '不饮酒', confirmed: false }
    ],
    errors: {},
    total: 564
  },
  onLoad(options) {
    const service = services.find((item) => item.id === options.id) || services[0]
    this.setData({ service, total: service.price * this.data.duration })
    const id = options.id || service.id
    request(`/v1/services/${encodeURIComponent(id)}`).then(detail => {
      const live = mapService(detail)
      this.setData({ service: live, total: live.price * this.data.duration,
        boundaries: (detail.boundaries || []).map(text => ({ text, confirmed: false })) })
      return Promise.all([
        request(`/v1/cosers/${encodeURIComponent(id)}/availability`),
        request(`/v1/venues?city=${encodeURIComponent(detail.city)}`)
      ])
    }).then(([availability, venues]) => {
      const slots = availability.slots || []
      const dates = [...new Set(slots.map(slot => slot.startAt.slice(0, 10)))].map(date => ({ date, label: date.slice(5).replace('-', '/'), week: '' }))
      this.setData({ slots, bookingDates: dates.slice(0, 14), venues, date: dates.length ? dates[0].date : '' }, () => this.refreshSchedule())
    }).catch(err => { this.setData({ isAvailable: false }); wx.showToast({ title: err.message || '服务暂不可用', icon: 'none' }) })
    if (wx.getStorageSync('account_token')) request('/v1/users/me').then(user => this.setData({ nickname: user.nickname || '' })).catch(() => {})
  },
  refreshSchedule(next = {}) {
    const startTime = next.startTime || this.data.startTime
    const duration = next.duration || this.data.duration
    const date = next.date || this.data.date
    const hours = buildHours(startTime, duration, date, this.data.slots)
    const selected = hours.filter((item) => item.selected)
    this.setData({ ...next, hours, isAvailable: !!date && selected.length === duration && selected.every((item) => item.open), total: this.data.service.price * duration })
  },
  updateDate(date) {
    this.refreshSchedule({ date })
  },
  onDate(event) { this.updateDate(event.detail.value) },
  selectBookingDate(event) { this.updateDate(event.currentTarget.dataset.date) },
  onTime(event) { this.refreshSchedule({ startTime: event.detail.value }) },
  decrement() { if (this.data.duration > 2) this.refreshSchedule({ duration: this.data.duration - 1 }) },
  increment() { if (this.data.duration < 8) this.refreshSchedule({ duration: this.data.duration + 1 }) },
  choosePlace() {
    if (!this.data.venues.length) return wx.showToast({ title: '暂无可选公共场所', icon: 'none' })
    wx.showActionSheet({ itemList: this.data.venues.map(item => item.name), success: ({ tapIndex }) => {
      const venue = this.data.venues[tapIndex]
      this.setData({ place: venue.name, placeDetails: venue, 'errors.place': '' })
    } })
  },
  onField(event) { this.setData({ [event.currentTarget.dataset.field]: event.detail.value }) },
  chooseBoundary(event) {
    this.setData({ contactBoundary: this.data.boundaryOptions[event.detail.value] })
  },
  toggleBoundary(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (!Number.isInteger(index) || index < 0 || index >= this.data.boundaries.length) return
    const boundaries = this.data.boundaries.map((item, itemIndex) => itemIndex === index
      ? { ...item, confirmed: !item.confirmed, invalid: false }
      : item)
    this.setData({ boundaries })
  },
  async submit() {
    if (!getApp().globalData.localDevMode && !guardBooking()) return
    const errors = {}
    if (!this.data.isAvailable) return wx.showToast({ title: '所选时段不可约', icon: 'none' })
    if (!wx.getStorageSync('account_token')) return wx.showModal({ title: '请先登录', content: '使用微信登录后即可提交委托。', confirmText: '去登录', success: ({ confirm }) => { if (confirm) wx.switchTab({ url: '/pages/profile/index' }) } })
    const boundaries = this.data.boundaries.map((item) => ({ ...item, invalid: !item.confirmed }))
    if (!this.data.placeDetails || !this.data.venues.some(item => item.id === this.data.placeDetails.id)) errors.place = '请选择平台核准的公共场所'
    if (!this.data.nickname.trim()) errors.nickname = '请告诉Coser如何称呼你'
    this.setData({ boundaries, errors })
    if (boundaries.some((item) => item.invalid)) {
      wx.pageScrollTo({ selector: '#boundary-section', duration: 300 })
      return
    }
    if (Object.keys(errors).length) return wx.showToast({ title: '请完善必填信息', icon: 'none' })
    wx.showLoading({ title: '创建订单' })
    try {
      const quote = await request('/v1/orders/quote', { method: 'POST', data: { serviceId: this.data.service.id, startAt: `${this.data.date}T${this.data.startTime}:00`, durationHours: this.data.duration } })
      const created = await request('/v1/orders', { method: 'POST', header: { 'Idempotency-Key': `booking-${Date.now()}-${Math.random().toString(36).slice(2)}` }, data: { quoteId: quote.quoteId, venueId: this.data.placeDetails.id } })
      const id = created.id
      const order = {
        id,
        title: this.data.service.title,
        coser: this.data.service.coser,
        coserGender: this.data.service.gender === '男' ? 'M' : 'F',
        customer: this.data.nickname.trim(),
        date: `${this.data.date} ${this.data.startTime}–${String(Number(this.data.startTime.slice(0, 2)) + this.data.duration).padStart(2, '0')}:00`,
        place: this.data.place,
        city: this.data.placeDetails.city,
        district: this.data.placeDetails.district,
        placeDetails: this.data.placeDetails,
        amount: Number(quote.payableAmount) / 100,
        boundaries: this.data.boundaries.map((item) => item.text),
        status: 'unpaid',
        statusText: '待支付'
      }
      set('orders', [order, ...get('orders', []).filter((item) => item.id !== id)])
      wx.navigateTo({ url: `/pages/order-detail/index?id=${id}&new=1` })
    } catch (err) { wx.showModal({ title: '创建失败', content: err.message || '请稍后重试', showCancel: false }) }
    finally { wx.hideLoading() }
  }
})
