const { services } = require('../../utils/services')
const { DISTRICTS, CITIES } = require('../../utils/cities')
const { get } = require('../../utils/store')
const { isBookableDay, nextDays } = require('../../utils/availability')

const filterOptions = {
  time: ['全部时间', '今天可约', '本周可约', '周末可约'],
  price: ['不限价格', '¥150以下', '¥150–200', '¥200以上'],
  credit: ['不限信用', '信用极好', '4.8分以上']
}

Page({
  data: {
    keyword: '',
    city: '上海',
    filters: [
      { key: 'time', label: '时间', value: '' },
      { key: 'price', label: '价格', value: '' },
      { key: 'district', label: '区域', value: '' },
      { key: 'credit', label: '信用', value: '' }
    ],
    filterSheetOpen: false,
    filterSheetKey: '',
    filterSheetTitle: '',
    filterSheetOptions: [],
    filterSheetSelected: '',
    results: services.filter((item) => item.gender === '女')
  },
  onLoad(options) {
    const city = CITIES.includes(options.city) ? options.city : get('currentCity', '上海')
    this.setData({ keyword: decodeURIComponent(options.q || ''), city }, this.applyFilters)
  },
  onKeyword(event) { this.setData({ keyword: event.detail.value }) },
  submit() { this.applyFilters() },
  chooseFilter(event) {
    const key = event.currentTarget.dataset.key
    const options = key === 'district' ? [`全${this.data.city}`, ...DISTRICTS[this.data.city]] : filterOptions[key]
    const label = this.data.filters.find((item) => item.key === key).label
    this.setData({ filterSheetOpen: true, filterSheetKey: key, filterSheetTitle: `选择${label}`, filterSheetOptions: options, filterSheetSelected: this.data.filters.find((item) => item.key === key).value })
  },
  closeFilterSheet() { this.setData({ filterSheetOpen: false }) },
  stopTap() {},
  selectFilterOption(event) {
    const value = event.currentTarget.dataset.value
    const key = this.data.filterSheetKey
    const filters = this.data.filters.map((item) => item.key === key ? { ...item, value } : item)
    this.setData({ filters, filterSheetOpen: false }, this.applyFilters)
  },
  clearFilters() { this.setData({ filters: this.data.filters.map((item) => ({ ...item, value: '' })) }, this.applyFilters) },
  applyFilters() {
    const selected = Object.fromEntries(this.data.filters.map((item) => [item.key, item.value]))
    const keyword = this.data.keyword.trim().toLowerCase()
    let results = services.filter((item) => (item.city || '上海') === this.data.city && (!keyword || `${item.title}${item.coser}${item.district}`.toLowerCase().includes(keyword)))
    if (selected.time && selected.time !== '全部时间') {
      const upcoming = nextDays({ available: true }, 7)
      const today = new Date()
      const daysLeftInWeek = 7 - ((today.getDay() + 6) % 7)
      const keys = selected.time === '今天可约' ? upcoming.slice(0, 1).map((day) => day.key)
        : selected.time === '周末可约' ? upcoming.slice(0, daysLeftInWeek).filter((day) => /周[六日]/.test(day.sub)).map((day) => day.key)
          : upcoming.slice(0, daysLeftInWeek).map((day) => day.key)
      results = results.filter((item) => keys.some((key) => isBookableDay(item, key)))
    }
    results = results.filter((item) => item.gender === '女')
    if (selected.price === '¥150以下') results = results.filter((item) => item.price < 150)
    if (selected.price === '¥150–200') results = results.filter((item) => item.price >= 150 && item.price <= 200)
    if (selected.price === '¥200以上') results = results.filter((item) => item.price > 200)
    if (selected.district && selected.district !== `全${this.data.city}`) results = results.filter((item) => item.district === selected.district)
    if (selected.credit === '信用极好') results = results.filter((item) => item.credit === 'excellent')
    if (selected.credit === '4.8分以上') results = results.filter((item) => item.rating >= 4.8)
    this.setData({ results })
  },
  openService(event) { wx.navigateTo({ url: `/pages/coser/index?id=${event.detail.id}` }) }
})
