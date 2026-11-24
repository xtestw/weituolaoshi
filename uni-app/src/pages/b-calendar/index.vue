<template>
<view class="page calendar-page">
  <view class="section">
    <view class="section__head"><text class="section__title">档期设置</text><button class="section__link batch-toggle" @tap="toggleBatch" :aria-expanded="batchOpen"><text>{{ batchOpen ? '收起批量设置' : '批量设置' }}</text><view :class="'control-chevron ' + (batchOpen ? 'control-chevron--up' : 'control-chevron--down') + ' control-chevron--active'" aria-hidden="true"></view></button></view>
    <view class="month-nav"><button :class="!canPrev ? 'month-nav__disabled' : ''" data-delta="-1" @tap="changeMonth" :disabled="busy || !canPrev" aria-label="上个月">‹</button><text>{{ year }}年{{ month }}月</text><button :class="!canNext ? 'month-nav__disabled' : ''" data-delta="1" @tap="changeMonth" :disabled="busy || !canNext" aria-label="下个月">›</button></view>
    <view class="week-head"><text>一</text><text>二</text><text>三</text><text>四</text><text>五</text><text>六</text><text>日</text></view>
    <view class="month-grid"><view v-for="item in days" :key="item.key" class="day-cell"><button v-if="!item.blank" :class="'day ' + dayClass(item)" :data-key="item.key" @tap="onDayTap" :disabled="busy || item.past" :aria-label="month + '月' + item.day + '日，' + item.label" :aria-pressed="item.open"><text>{{ item.day }}</text><text>{{ item.label }}</text></button></view></view>
    <view class="calendar-legend"><view><view class="legend-dot legend-dot--open"></view><text>可约</text></view><view><view class="legend-dot legend-dot--occupied"></view><text>已占用</text></view><view><view class="legend-dot"></view><text>不可约</text></view></view>
    <text v-if="error" class="calendar-error">{{ error }}</text>
    <text class="calendar-hint">默认不可约。开放日营业时段为 {{ windowLabel }}。点击日期切换可约状态；已占用日期不可关闭。可设置至 {{ maxYear }}年12月。</text>
  </view>
  <view v-if="batchOpen" class="section batch"><view class="section__head"><text class="section__title">批量设置 · {{ year }}年{{ month }}月</text></view><button v-for="(item, index) in batchOptions" :key="item" :class="lastBatchIndex === index ? 'batch__applied' : ''" :data-index="index" @tap="applyBatch" :disabled="busy">{{ item }}<text>{{ lastBatchIndex === index ? '已应用' : '›' }}</text></button><text class="batch__hint">批量设置作用于当前月份未来日期；已占用日期会自动跳过。</text></view>
</view>

<b-tabbar selected="calendar"/>
</template>
<script>
import BTabbar from '../../components/b-tabbar/index.vue'
import { request } from '../../services/api'

const BATCH = ['全部可以接单', '全部不可接单', '工作日可接单', '周末可接单']
const BATCH_TYPES = ['all_open', 'all_closed', 'weekdays_open', 'weekends_open']
const today = new Date()
const MIN_MONTH = today.getFullYear() * 12 + today.getMonth()
const MAX_MONTH = (today.getFullYear() + 1) * 12 + 11
const keyFor = (year, month, day) => `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`

export default {
components: { BTabbar },
data() {
  return {
    year: today.getFullYear(),
    month: today.getMonth() + 1,
    monthIndex: MIN_MONTH,
    maxYear: today.getFullYear() + 1,
    canPrev: false,
    canNext: true,
    days: [],
    batchOpen: false,
    batchOptions: BATCH,
    lastBatchIndex: -1,
    selectedKey: '',
    busy: false,
    error: '',
    windowLabel: '10:00–22:00'
  }
},
onShow() { this.loadMonth(this.monthIndex) },
methods: {
dayClass(item) {
  if (item.status === 'occupied') return 'day--occupied' + (this.selectedKey === item.key ? ' day--selected' : '')
  if (item.open) return 'day--open' + (this.selectedKey === item.key ? ' day--selected' : '')
  return 'day--closed' + (this.selectedKey === item.key ? ' day--selected' : '')
},
async loadMonth(monthIndex, selectedKey = '') {
  const year = Math.floor(monthIndex / 12)
  const month = monthIndex % 12 + 1
  const monthKey = `${year}-${String(month).padStart(2, '0')}`
  this.setData({ busy: true, error: '' })
  try {
    const result = await request(`/v1/cosers/me/availability?month=${monthKey}`)
    const byDate = new Map((result.days || []).map(day => [day.date, day]))
    const leading = (new Date(year, month - 1, 1).getDay() + 6) % 7
    const count = new Date(year, month, 0).getDate()
    const current = new Date()
    const currentKey = keyFor(current.getFullYear(), current.getMonth() + 1, current.getDate())
    const days = Array.from({ length: leading }, (_, index) => ({ key: `blank-${index}`, blank: true }))
    for (let day = 1; day <= count; day++) {
      const key = keyFor(year, month, day)
      const info = byDate.get(key) || { open: false, status: 'closed', editable: key >= currentKey, occupiedCount: 0 }
      const past = key < currentKey
      const status = info.status || (info.open ? 'open' : 'closed')
      const label = status === 'occupied' ? '已约' : (info.open ? '可约' : '不可约')
      days.push({
        key, day, open: !!info.open, past, status,
        editable: !past && info.editable !== false && status !== 'occupied',
        occupiedCount: info.occupiedCount || 0,
        label
      })
    }
    this.setData({
      year, month, monthIndex,
      canPrev: monthIndex > MIN_MONTH,
      canNext: monthIndex < MAX_MONTH,
      days, selectedKey,
      windowLabel: result.windowLabel || '10:00–22:00'
    })
    return true
  } catch (error) {
    this.setData({ error: error.message || '档期加载失败，请重试' })
    return false
  } finally { this.setData({ busy: false }) }
},
changeMonth(event) {
  if (this.busy) return
  const next = this.monthIndex + Number(event.currentTarget.dataset.delta)
  if (next < MIN_MONTH || next > MAX_MONTH) return
  this.setData({ lastBatchIndex: -1 })
  this.loadMonth(next)
},
onDayTap(event) {
  if (this.busy) return
  const key = event.currentTarget.dataset.key
  const day = this.days.find(item => item.key === key)
  if (!day || day.past) return
  if (day.status === 'occupied' || !day.editable) {
    this.setData({ selectedKey: key })
    return uni.showModal({
      title: `${this.month}月${day.day}日已占用`,
      content: `该日已有进行中的委托（${day.occupiedCount || 1}笔），不能关闭或重设档期。可在订单页查看详情。`,
      confirmText: '查看订单',
      success: ({ confirm }) => { if (confirm) uni.navigateTo({ url: '/pages/b-orders/index' }) }
    })
  }
  this.toggleDay(day)
},
async toggleDay(day) {
  this.setData({ busy: true, error: '', selectedKey: day.key })
  try {
    await request(`/v1/cosers/me/availability/${day.key}`, { method: 'PUT', data: { open: !day.open } })
    await this.loadMonth(this.monthIndex, day.key)
  } catch (error) {
    this.setData({ error: error.message || '保存失败，请重试' })
  } finally { this.setData({ busy: false }) }
},
toggleBatch() { this.setData({ batchOpen: !this.batchOpen }) },
async applyBatch(event) {
  if (this.busy) return
  const index = Number(event.currentTarget.dataset.index)
  const type = BATCH_TYPES[index]
  if (!type) return
  const month = `${this.year}-${String(this.month).padStart(2, '0')}`
  this.setData({ busy: true, error: '' })
  try {
    const result = await request('/v1/cosers/me/availability/batch', { method: 'POST', data: { month, type } })
    if (await this.loadMonth(this.monthIndex, this.selectedKey)) {
      this.setData({ lastBatchIndex: index })
      const skipped = result.skippedDays || 0
      uni.showToast({
        title: skipped ? `已更新 ${result.changedDays || 0} 天，跳过 ${skipped} 天占用` : '本月日历已更新',
        icon: 'none'
      })
    }
  } catch (error) {
    this.setData({ error: error.message || '批量设置失败，请重试' })
  } finally { this.setData({ busy: false }) }
},
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.calendar-page { padding-top: 4rpx; padding-bottom: calc(180rpx + env(safe-area-inset-bottom)); }
.section__head button.section__link { min-height: 72rpx; margin: 0; padding: 0 18rpx; display: flex; align-items: center; justify-content: center; gap: 12rpx; border: 2rpx solid #ddd6fe; border-radius: 18rpx; background: var(--color-primary-soft); color: var(--color-primary-ink); font-size: 24rpx; font-weight: 700; }
.batch-toggle .control-chevron { width: 12rpx; height: 12rpx; }
.month-nav { display: flex; align-items: center; justify-content: space-between; margin: 8rpx 0 16rpx; font-size: 30rpx; font-weight: 750; }
.month-nav button { width: 88rpx; height: 88rpx; margin: 0; padding: 0; border: 0; border-radius: 50%; background: var(--color-primary-soft); color: var(--color-primary); font-size: 42rpx; }
.month-nav__disabled { background: #f5f4f8; color: #bdb7c4; }
.week-head,.month-grid { display: grid; grid-template-columns: repeat(7,minmax(0,1fr)); gap: 6rpx; }
.week-head text { text-align: center; color: var(--color-muted); font-size: 22rpx; }
.month-grid { margin-top: 10rpx; }
.day-cell { min-width: 0; min-height: 88rpx; }
.day { width: 100%; min-height: 88rpx; margin: 0; padding: 5rpx 0; display: flex; flex-direction: column; align-items: center; justify-content: center; border: 2rpx solid transparent; border-radius: 14rpx; background: var(--color-bg); color: var(--color-muted); font-size: 22rpx; line-height: 1.2; }
.day text:first-child { color: var(--color-text); font-size: 28rpx; font-weight: 750; }
.day--open { background: var(--color-success-soft); color: var(--color-success); }
.day--occupied { background: #fff4e5; color: #b54708; }
.day--occupied text:first-child { color: #93370d; }
.day--selected { border-color: var(--color-primary); }
.day[disabled] { opacity: .5; }
.calendar-legend { margin-top: 18rpx; display: flex; gap: 18rpx; color: var(--color-muted); font-size: 22rpx; }
.calendar-legend>view { display: flex; align-items: center; gap: 8rpx; }
.legend-dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: #d8d4de; }
.legend-dot--open { background: var(--color-success); }
.legend-dot--occupied { background: #f79009; }
.calendar-error { display: block; margin-top: 20rpx; color: #b42339; font-size: 24rpx; line-height: 1.45; }
.calendar-hint,.batch__hint { display: block; margin-top: 20rpx; color: var(--color-muted); font-size: 23rpx; }
.batch { padding-top: 18rpx; padding-bottom: 18rpx; }
.batch button { width: 100%; min-height: 84rpx; margin: 0; padding: 0 4rpx; display: flex; align-items: center; justify-content: space-between; border: 0; border-bottom: 1rpx solid var(--color-border); background: transparent; color: var(--color-text); text-align: left; font-size: 26rpx; }
.batch button text { color: var(--color-muted-soft); }
.batch button[disabled] { opacity: .55; }
.batch__applied { color: var(--color-primary-ink); font-weight: 750; }
.batch__applied text { color: var(--color-success); }

</style>
