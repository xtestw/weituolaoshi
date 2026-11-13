const { services } = require('../../utils/services')
const { CITIES } = require('../../utils/cities')
const { get, set } = require('../../utils/store')
const { isBookableDay } = require('../../utils/availability')

function buildCalendarDays() {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()
  const leading = (new Date(year, month, 1).getDay() + 6) % 7
  const count = new Date(year, month + 1, 0).getDate()
  const blanks = Array.from({ length: leading }, (_, index) => ({ key: `blank-${index}`, blank: true }))
  const days = Array.from({ length: count }, (_, index) => ({
    key: `day-${index + 1}`,
    day: index + 1,
    blank: false,
    past: index + 1 < now.getDate()
  }))
  return blanks.concat(days)
}

Page({
  data: {
    city: '上海',
    cities: CITIES,
    cityPickerOpen: false,
    keyword: '',
    activeFeed: 'recommend',
    dateLabel: '日历可约',
    calendarOpen: false,
    calendarSelected: false,
    calendarWeekdays: ['一', '二', '三', '四', '五', '六', '日'],
    calendarDays: buildCalendarDays(),
    calendarMonthLabel: `${new Date().getFullYear()}年${new Date().getMonth() + 1}月`,
    calendarMonthNumber: new Date().getMonth() + 1,
    calendarStart: new Date().getDate(),
    calendarEnd: Math.min(new Date().getDate() + 6, new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0).getDate()),
    feeds: [
      { key: 'following', label: '关注' },
      { key: 'recommend', label: '推荐' },
      { key: 'calendar', label: '日历可约' },
      { key: 'topic', label: '热门专题' }
    ],
    left: [services[0], services[2]],
    right: [services[4], services[3]]
  },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 0, hidden: false })
    const city = get('currentCity', '上海')
    if (city !== this.data.city) this.setData({ city }, () => this.applyFeed(this.data.activeFeed))
    this.showWomenOnlyNotice()
  },
  showWomenOnlyNotice() {
    let did = wx.getStorageSync('coser_v1_local_did')
    if (!did) {
      did = `did-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
      wx.setStorageSync('coser_v1_local_did', did)
    }
    const key = `coser_v1_women_notice_${did}`
    if (wx.getStorageSync(key)) return
    wx.setStorageSync(key, true)
    wx.showModal({ title: '欢迎来到全女平台', content: '这里是全女委托社区。你可以自由浏览；下单和发布前，我们会核验女性身份及成年状态。（占位文案，后续调整）', showCancel: false, confirmText: '我知道了' })
  },
  selectCity() {
    this.setData({ cityPickerOpen: true })
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ hidden: true })
  },
  closeCityPicker() {
    this.setData({ cityPickerOpen: false })
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ hidden: false })
  },
  chooseCity(event) {
    const city = event.currentTarget.dataset.city
    if (!CITIES.includes(city)) return
    set('currentCity', city)
    getApp().globalData.currentCity = city
    this.setData({ city, cityPickerOpen: false }, () => this.applyFeed(this.data.activeFeed))
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ hidden: false })
  },
  onKeyword(event) { this.setData({ keyword: event.detail.value }) },
  submitSearch() { wx.navigateTo({ url: `/pages/search/index?q=${encodeURIComponent(this.data.keyword)}&city=${encodeURIComponent(this.data.city)}` }) },
  selectFeed(event) {
    const key = event.currentTarget.dataset.key
    if (key === 'calendar') {
      wx.nextTick(() => {
        this.setData({ calendarOpen: true })
        if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ hidden: true })
      })
      return
    }
    this.applyFeed(key)
  },
  applyFeed(key) {
    const groups = {
      following: [services[0], services[3]],
      recommend: [services[0], services[2], services[4], services[3]],
      calendar: services.filter((item) => {
        if (item.gender !== '女') return false
        if (!this.data.calendarSelected) return item.available
        const { calendarStart, calendarEnd, calendarMonthNumber } = this.data
        const year = new Date().getFullYear()
        const keys = Array.from({ length: calendarEnd - calendarStart + 1 }, (_, offset) => `${year}-${String(calendarMonthNumber).padStart(2, '0')}-${String(calendarStart + offset).padStart(2, '0')}`)
        return keys.some((key) => isBookableDay(item, key))
      }),
      topic: [services[2], services[4], services[1]]
    }
    const items = (groups[key] || groups.recommend).filter((item) => (item.city || '上海') === this.data.city)
    this.setData({ activeFeed: key, left: items.filter((_, index) => index % 2 === 0), right: items.filter((_, index) => index % 2 === 1) })
  },
  closeCalendar() {
    this.setData({ calendarOpen: false })
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ hidden: false })
  },
  stopCalendar() {},
  chooseCalendarDay(event) {
    const day = Number(event.currentTarget.dataset.day)
    if (!day || day < new Date().getDate()) return
    const { calendarStart, calendarEnd } = this.data
    if (calendarStart && calendarEnd) {
      this.setData({ calendarStart: day, calendarEnd: null })
      return
    }
    if (!calendarStart || day < calendarStart) {
      this.setData({ calendarStart: day, calendarEnd: null })
      return
    }
    this.setData({ calendarEnd: day })
  },
  confirmCalendar() {
    const { calendarStart, calendarEnd } = this.data
    if (!calendarStart || !calendarEnd) {
      wx.showToast({ title: '请选择开始和结束日期', icon: 'none' })
      return
    }
    const monthText = String(this.data.calendarMonthNumber).padStart(2, '0')
    const label = `${monthText}.${String(calendarStart).padStart(2, '0')}-${monthText}.${String(calendarEnd).padStart(2, '0')}可约`
    const year = new Date().getFullYear()
    const month = this.data.calendarMonthNumber
    const keys = Array.from({ length: calendarEnd - calendarStart + 1 }, (_, offset) => `${year}-${String(month).padStart(2, '0')}-${String(calendarStart + offset).padStart(2, '0')}`)
    const items = services.filter((item) => item.gender === '女' && (item.city || '上海') === this.data.city && keys.some((key) => isBookableDay(item, key)))
    this.setData({ calendarOpen: false, calendarSelected: true, activeFeed: 'calendar', dateLabel: label, 'feeds[2].label': label, left: items.filter((_, index) => index % 2 === 0), right: items.filter((_, index) => index % 2 === 1) })
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ hidden: false })
  },
  openService(event) { wx.navigateTo({ url: `/pages/coser/index?id=${event.detail.id}` }) },
  onShareAppMessage() { return { title: '次元委托｜寻找同城可约 Coser', path: '/pages/home/index' } },
  onPullDownRefresh() { setTimeout(() => wx.stopPullDownRefresh(), 500) }
})
