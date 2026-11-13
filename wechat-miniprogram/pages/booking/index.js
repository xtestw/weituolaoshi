const { get, set } = require('../../utils/store')
const { services } = require('../../utils/services')
const { validateVenue } = require('../../utils/cities')
const { keyForDate, isBookableDay, nextDays } = require('../../utils/availability')
const { guardBooking } = require('../../utils/eligibility')

const withinServiceHours = (startTime, duration) => {
  const [hour, minute] = String(startTime).split(':').map(Number)
  const start = hour * 60 + minute
  return Number.isFinite(start) && start >= 7 * 60 && start + duration * 60 <= 21 * 60
}
const hoursUntil = (date, startTime) => (new Date(`${date}T${startTime}:00`).getTime() - Date.now()) / 3600000

Page({
  data: {
    service: services[0],
    date: '',
    minDate: '',
    bookingDates: [],
    startTime: '14:00',
    duration: 3,
    dayAvailable: true,
    timeAllowed: true,
    isAvailable: true,
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
    chatOpen: false,
    chatText: '',
    chatMessages: [
      { id: 'm1', text: '你好呀，可以先确认时间、集合地点和相处边界～', mine: false }
    ],
    urgent: false,
    chatConfirmed: false,
    errors: {},
    total: 564
  },
  onLoad(options) {
    const service = services.find((item) => item.id === options.id) || services[0]
    const bookingDates = nextDays(service, 14).map((item) => ({ ...item, date: item.key, week: item.sub }))
    const minDate = keyForDate(new Date())
    const date = options.date && isBookableDay(service, options.date) ? options.date : minDate
    const dayAvailable = isBookableDay(service, date)
    const timeAllowed = withinServiceHours(this.data.startTime, this.data.duration)
    this.setData({ service, bookingDates, minDate, date, dayAvailable, timeAllowed, isAvailable: dayAvailable && timeAllowed && hoursUntil(date, this.data.startTime) > 0, urgent: hoursUntil(date, this.data.startTime) > 0 && hoursUntil(date, this.data.startTime) < 24, total: service.price * this.data.duration })
  },
  refreshSchedule(next = {}) {
    const startTime = next.startTime || this.data.startTime
    const duration = next.duration || this.data.duration
    const timeAllowed = withinServiceHours(startTime, duration)
    const hoursToStart = hoursUntil(this.data.date, startTime)
    this.setData({ ...next, timeAllowed, isAvailable: this.data.dayAvailable && timeAllowed && hoursToStart > 0, urgent: hoursToStart > 0 && hoursToStart < 24, chatConfirmed: false, total: this.data.service.price * duration })
  },
  updateDate(date) {
    const hoursToStart = hoursUntil(date, this.data.startTime)
    const dayAvailable = isBookableDay(this.data.service, date)
    this.setData({ date, dayAvailable, isAvailable: dayAvailable && this.data.timeAllowed && hoursToStart > 0, urgent: hoursToStart > 0 && hoursToStart < 24, chatConfirmed: false })
  },
  onDate(event) { this.updateDate(event.detail.value) },
  selectBookingDate(event) { this.updateDate(event.currentTarget.dataset.date) },
  onTime(event) { this.refreshSchedule({ startTime: event.detail.value }) },
  decrement() { if (this.data.duration > 2) this.refreshSchedule({ duration: this.data.duration - 1 }) },
  increment() { if (this.data.duration < 8) this.refreshSchedule({ duration: this.data.duration + 1 }) },
  choosePlace() {
    wx.chooseLocation({ success: (location) => {
      const result = validateVenue(location, this.data.service.city || '上海')
      if (!result.valid) return wx.showToast({ title: result.reason, icon: 'none' })
      this.setData({ place: result.place, placeDetails: result, 'errors.place': '' })
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
  openChat() { this.setData({ chatOpen: true }) },
  closeChat() { this.setData({ chatOpen: false }) },
  stopTap() {},
  onChatText(event) { this.setData({ chatText: event.detail.value }) },
  sendChat() {
    const text = this.data.chatText.trim()
    if (!text) return
    this.setData({ chatMessages: this.data.chatMessages.concat({ id: `m-${Date.now()}`, text, mine: true }), chatText: '' })
  },
  confirmChatTime() {
    this.setData({ chatConfirmed: true, chatMessages: this.data.chatMessages.concat({ id: `m-${Date.now()}`, text: `已确认 ${this.data.date} ${this.data.startTime} 开始。付款后仍需Coser确认订单。`, mine: false }) })
  },
  submit() {
    if (!guardBooking()) return
    const errors = {}
    if (!this.data.dayAvailable) return wx.showToast({ title: '所选日期不可约', icon: 'none' })
    if (!this.data.timeAllowed) return wx.showToast({ title: '服务须在07:00–21:00内结束', icon: 'none' })
    if (hoursUntil(this.data.date, this.data.startTime) <= 0) return wx.showToast({ title: '请选择未来时间', icon: 'none' })
    if (this.data.urgent && !this.data.chatConfirmed) {
      this.setData({ chatOpen: true })
      return wx.showToast({ title: '请先在私信中确认时间', icon: 'none' })
    }
    const boundaries = this.data.boundaries.map((item) => ({ ...item, invalid: !item.confirmed }))
    if (!this.data.placeDetails || !validateVenue(this.data.placeDetails, this.data.service.city || '上海').valid) errors.place = `请选择${this.data.service.city || '上海'}开放城区内的公共场所`
    if (!this.data.nickname.trim()) errors.nickname = '请告诉Coser如何称呼你'
    this.setData({ boundaries, errors })
    if (boundaries.some((item) => item.invalid)) {
      wx.pageScrollTo({ selector: '#boundary-section', duration: 300 })
      return
    }
    if (Object.keys(errors).length) return wx.showToast({ title: '请完善必填信息', icon: 'none' })
    wx.showLoading({ title: '创建订单' })
    setTimeout(() => {
      wx.hideLoading()
      const id = `WT${Date.now()}`
      const order = {
        id,
        title: this.data.service.title,
        coser: this.data.service.coser,
        coserGender: 'F',
        customer: this.data.nickname.trim(),
        date: `${this.data.date} ${this.data.startTime}–${String(Math.floor((Number(this.data.startTime.slice(0, 2)) * 60 + Number(this.data.startTime.slice(3, 5)) + this.data.duration * 60) / 60)).padStart(2, '0')}:${this.data.startTime.slice(3, 5)}`,
        place: this.data.place,
        city: this.data.placeDetails.city,
        district: this.data.placeDetails.district,
        placeDetails: this.data.placeDetails,
        amount: this.data.total,
        boundaries: this.data.boundaries.map((item) => item.text),
        status: 'unpaid',
        statusText: '待实名并付款'
      }
      set('orders', [order, ...get('orders', []).filter((item) => item.id !== id)])
      wx.navigateTo({ url: `/pages/order-detail/index?id=${id}&new=1` })
    }, 500)
  }
})
