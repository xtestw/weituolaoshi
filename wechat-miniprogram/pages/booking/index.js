const { get, set } = require('../../utils/store')
const { services } = require('../../utils/services')
const { validateVenue } = require('../../utils/cities')

const isOpenHour = (hour) => (hour >= 10 && hour < 12) || (hour >= 14 && hour < 18) || (hour >= 19 && hour < 22)
const buildHours = (startTime, duration) => {
  const start = Number(String(startTime).split(':')[0])
  return Array.from({ length: 24 }, (_, hour) => ({
    label: String(hour).padStart(2, '0'),
    open: isOpenHour(hour),
    selected: hour >= start && hour < start + duration
  }))
}

Page({
  data: {
    service: services[0],
    date: '2026-11-16',
    bookingDates: [
      { date: '2026-11-16', label: '11/16', week: '周一' },
      { date: '2026-11-17', label: '11/17', week: '周二' },
      { date: '2026-11-21', label: '11/21', week: '周六' },
      { date: '2026-11-22', label: '11/22', week: '周日' }
    ],
    startTime: '14:00',
    duration: 3,
    hours: buildHours('14:00', 3),
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
    this.setData({ service, total: service.price * this.data.duration })
  },
  refreshSchedule(next = {}) {
    const startTime = next.startTime || this.data.startTime
    const duration = next.duration || this.data.duration
    const hours = buildHours(startTime, duration)
    const selected = hours.filter((item) => item.selected)
    this.setData({ ...next, hours, isAvailable: selected.length === duration && selected.every((item) => item.open), total: this.data.service.price * duration })
  },
  updateDate(date) {
    const hoursToStart = (new Date(`${date}T${this.data.startTime}:00`).getTime() - Date.now()) / 3600000
    this.setData({ date, urgent: hoursToStart > 0 && hoursToStart < 24, chatConfirmed: false })
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
    this.setData({ chatConfirmed: true, chatMessages: this.data.chatMessages.concat({ id: `m-${Date.now()}`, text: `已确认 ${this.data.date} ${this.data.startTime} 开始，档期暂时保留30分钟。`, mine: false }) })
  },
  submit() {
    const errors = {}
    if (!this.data.isAvailable) return wx.showToast({ title: '所选时段不可约', icon: 'none' })
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
        coserGender: this.data.service.gender === '男' ? 'M' : 'F',
        customer: this.data.nickname.trim(),
        date: `${this.data.date} ${this.data.startTime}–${String(Number(this.data.startTime.slice(0, 2)) + this.data.duration).padStart(2, '0')}:00`,
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
