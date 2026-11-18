const { request } = require('../../services/api')

const BATCH = ['全部可以接单', '全部不可接单', '工作日可接单', '周末可接单']
const BATCH_TYPES = ['all_open', 'all_closed', 'weekdays_open', 'weekends_open']
const today = new Date()
const MIN_MONTH = today.getFullYear() * 12 + today.getMonth()
const MAX_MONTH = (today.getFullYear() + 1) * 12 + 11
const keyFor = (year, month, day) => `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`

Page({
  data: { year: today.getFullYear(), month: today.getMonth() + 1, monthIndex: MIN_MONTH, maxYear: today.getFullYear() + 1, canPrev: false, canNext: true, days: [], batchOpen: false, batchOptions: BATCH, lastBatchIndex: -1, selectedKey: '', busy: false, error: '' },
  onShow() { this.loadMonth(this.data.monthIndex) },
  async loadMonth(monthIndex, selectedKey = '') {
    const year = Math.floor(monthIndex / 12)
    const month = monthIndex % 12 + 1
    const monthKey = `${year}-${String(month).padStart(2, '0')}`
    this.setData({ busy: true, error: '' })
    try {
      const result = await request(`/v1/cosers/me/availability?month=${monthKey}`)
      const openDates = new Set(result.days.filter(day => day.open).map(day => day.date))
      const leading = (new Date(year, month - 1, 1).getDay() + 6) % 7
      const count = new Date(year, month, 0).getDate()
      const current = new Date()
      const currentKey = keyFor(current.getFullYear(), current.getMonth() + 1, current.getDate())
      const days = Array.from({ length: leading }, (_, index) => ({ key: `blank-${index}`, blank: true }))
      for (let day = 1; day <= count; day++) {
        const key = keyFor(year, month, day)
        const open = openDates.has(key)
        days.push({ key, day, open, past: key < currentKey, label: open ? '可约' : '不可约' })
      }
      this.setData({ year, month, monthIndex, canPrev: monthIndex > MIN_MONTH, canNext: monthIndex < MAX_MONTH, days, selectedKey })
      return true
    } catch (error) {
      this.setData({ error: error.message || '档期加载失败，请重试' })
      return false
    } finally { this.setData({ busy: false }) }
  },
  changeMonth(event) {
    if (this.data.busy) return
    const next = this.data.monthIndex + Number(event.currentTarget.dataset.delta)
    if (next < MIN_MONTH || next > MAX_MONTH) return
    this.setData({ lastBatchIndex: -1 })
    this.loadMonth(next)
  },
  async toggleDay(event) {
    if (this.data.busy) return
    const key = event.currentTarget.dataset.key
    const day = this.data.days.find(item => item.key === key)
    if (!day || day.past) return
    this.setData({ busy: true, error: '' })
    try {
      await request(`/v1/cosers/me/availability/${key}`, { method: 'PUT', data: { open: !day.open } })
      await this.loadMonth(this.data.monthIndex, key)
    } catch (error) {
      this.setData({ error: error.message || '保存失败，请重试' })
    } finally { this.setData({ busy: false }) }
  },
  toggleBatch() { this.setData({ batchOpen: !this.data.batchOpen }) },
  async applyBatch(event) {
    if (this.data.busy) return
    const index = Number(event.currentTarget.dataset.index)
    const type = BATCH_TYPES[index]
    if (!type) return
    const month = `${this.data.year}-${String(this.data.month).padStart(2, '0')}`
    this.setData({ busy: true, error: '' })
    try {
      await request('/v1/cosers/me/availability/batch', { method: 'POST', data: { month, type } })
      if (await this.loadMonth(this.data.monthIndex, this.data.selectedKey)) {
        this.setData({ lastBatchIndex: index })
        wx.showToast({ title: '本月日历已更新', icon: 'none' })
      }
    } catch (error) {
      this.setData({ error: error.message || '批量设置失败，请重试' })
    } finally { this.setData({ busy: false }) }
  }
})
