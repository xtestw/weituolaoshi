const { get, set } = require('../../utils/store')
const BATCH = ['全部可以接单', '全部不可接单', '工作日可接单', '周末可接单']
const MIN_MONTH = 2026 * 12 + 10 // 2026-11
const MAX_MONTH = 2027 * 12 + 11 // 2027-12
const keyFor = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const isOpen = (value) => value === true || (Array.isArray(value) && value.length > 0)

Page({
  data: { year: 2026, month: 11, monthIndex: MIN_MONTH, canPrev: false, canNext: true, days: [], batchOpen: false, batchOptions: BATCH, lastBatchIndex: -1, selectedKey: '' },
  onLoad() {
    const saved = get('coserAvailability', {})
    const migrated = Object.keys(saved).reduce((result, key) => { result[key] = isOpen(saved[key]); return result }, {})
    set('coserAvailability', migrated)
    this.renderMonth(MIN_MONTH)
  },
  renderMonth(monthIndex, selectedKey = '') {
    const year = Math.floor(monthIndex / 12)
    const month = monthIndex % 12 + 1
    const first = new Date(year, month - 1, 1)
    const leading = (first.getDay() + 6) % 7
    const count = new Date(year, month, 0).getDate()
    const saved = get('coserAvailability', {})
    const days = Array.from({ length: leading }, (_, index) => ({ key: `blank-${index}`, blank: true }))
    for (let day = 1; day <= count; day++) {
      const date = new Date(year, month - 1, day)
      const key = keyFor(date)
      const open = isOpen(saved[key])
      days.push({ key, day, open, label: open ? '可约' : '不可约' })
    }
    this.setData({ year, month, monthIndex, canPrev: monthIndex > MIN_MONTH, canNext: monthIndex < MAX_MONTH, days, selectedKey })
  },
  changeMonth(event) {
    const next = this.data.monthIndex + Number(event.currentTarget.dataset.delta)
    if (next < MIN_MONTH || next > MAX_MONTH) return
    this.setData({ lastBatchIndex: -1 })
    this.renderMonth(next)
  },
  toggleDay(event) {
    const key = event.currentTarget.dataset.key
    const saved = get('coserAvailability', {})
    saved[key] = !isOpen(saved[key])
    set('coserAvailability', saved)
    this.renderMonth(this.data.monthIndex, key)
  },
  toggleBatch() { this.setData({ batchOpen: !this.data.batchOpen }) },
  applyBatch(event) {
    const type = Number(event.currentTarget.dataset.index)
    const saved = get('coserAvailability', {})
    const { year, month } = this.data
    const count = new Date(year, month, 0).getDate()
    for (let day = 1; day <= count; day++) {
      const date = new Date(year, month - 1, day)
      const weekday = date.getDay() !== 0 && date.getDay() !== 6
      if (type === 0 || (type === 2 && weekday) || (type === 3 && !weekday)) saved[keyFor(date)] = true
      if (type === 1) saved[keyFor(date)] = false
    }
    set('coserAvailability', saved)
    this.setData({ lastBatchIndex: type })
    this.renderMonth(this.data.monthIndex, this.data.selectedKey)
    wx.showToast({ title: '本月日历已更新', icon: 'none' })
  }
})
