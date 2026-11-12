const { services } = require('../../utils/services')
const { CITIES } = require('../../utils/cities')
const { get, set } = require('../../utils/store')

function buildCalendarDays() {
  const blanks = Array.from({ length: 6 }, (_, index) => ({ key: `blank-${index}`, blank: true }))
  const days = Array.from({ length: 30 }, (_, index) => ({
    key: `day-${index + 1}`,
    day: index + 1,
    blank: false
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
    calendarWeekdays: ['一', '二', '三', '四', '五', '六', '日'],
    calendarDays: buildCalendarDays(),
    calendarStart: 16,
    calendarEnd: 22,
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
      calendar: services.filter((item) => item.available && item.gender === '女'),
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
    if (!day) return
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
    const label = `11.${String(calendarStart).padStart(2, '0')}-11.${String(calendarEnd).padStart(2, '0')}可约`
    const items = services.filter((item) => item.available && item.gender === '女' && (item.city || '上海') === this.data.city)
    this.setData({ calendarOpen: false, activeFeed: 'calendar', dateLabel: label, 'feeds[2].label': label, left: items.filter((_, index) => index % 2 === 0), right: items.filter((_, index) => index % 2 === 1) })
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ hidden: false })
  },
  openService(event) { wx.navigateTo({ url: `/pages/coser/index?id=${event.detail.id}` }) },
  onShareAppMessage() { return { title: '次元委托｜寻找同城可约 Coser', path: '/pages/home/index' } },
  onPullDownRefresh() { setTimeout(() => wx.stopPullDownRefresh(), 500) }
})
