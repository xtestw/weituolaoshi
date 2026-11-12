const { services } = require('../../utils/services')
const { DISTRICTS, CITIES } = require('../../utils/cities')
const { get } = require('../../utils/store')

const filterOptions = {
  time: ['全部时间', '今天可约', '本周可约', '周末可约'],
  gender: ['同性 · 女'],
  price: ['不限价格', '¥150以下', '¥150–200', '¥200以上'],
  credit: ['不限信用', '信用极好', '4.8分以上']
}

Page({
  data: {
    keyword: '',
    city: '上海',
    filters: [
      { key: 'time', label: '时间', value: '' },
      { key: 'gender', label: '性别', value: '同性 · 女' },
      { key: 'price', label: '价格', value: '' },
      { key: 'district', label: '区域', value: '' },
      { key: 'credit', label: '信用', value: '' }
    ],
    userGender: '女',
    filterSheetOpen: false,
    filterSheetKey: '',
    filterSheetTitle: '',
    filterSheetOptions: [],
    filterSheetSelected: '',
    results: services.filter((item) => item.gender === '女')
  },
  onLoad(options) {
    const city = CITIES.includes(options.city) ? options.city : get('currentCity', '上海')
    const verification = wx.getStorageSync('coser_v1_verification')
    const userGender = verification && verification.gender === 'M' ? '男' : '女'
    const filters = this.data.filters.map((item) => item.key === 'gender' ? { ...item, value: `同性 · ${userGender}` } : item)
    this.setData({ keyword: decodeURIComponent(options.q || ''), city, userGender, filters }, this.applyFilters)
  },
  onKeyword(event) { this.setData({ keyword: event.detail.value }) },
  submit() { this.applyFilters() },
  chooseFilter(event) {
    const key = event.currentTarget.dataset.key
    if (key === 'gender') return wx.showToast({ title: `平台仅展示同性Coser（${this.data.userGender}）`, icon: 'none' })
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
  clearFilters() { this.setData({ filters: this.data.filters.map((item) => ({ ...item, value: item.key === 'gender' ? `同性 · ${this.data.userGender}` : '' })) }, this.applyFilters) },
  applyFilters() {
    const selected = Object.fromEntries(this.data.filters.map((item) => [item.key, item.value]))
    const keyword = this.data.keyword.trim().toLowerCase()
    let results = services.filter((item) => (item.city || '上海') === this.data.city && (!keyword || `${item.title}${item.coser}${item.district}`.toLowerCase().includes(keyword)))
    if (selected.time && selected.time !== '全部时间') results = results.filter((item) => item.available)
    results = results.filter((item) => item.gender === this.data.userGender)
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
