<template>
<view class="page page--no-tab">
  <view class="section service-summary"><view class="summary-cover"><text>{{ service.mark }}</text></view><view><text class="section__title">{{ service.title }}</text><text class="muted">{{ service.coser }} · {{ service.city }} <text v-if="service.verified">· 真人认证</text></text><text class="price">¥{{ service.price }}<text class="price__unit">/小时</text></text></view></view>

  <view class="section">
    <view class="section__head"><text class="section__title">时间与时长</text><text :class="'tag ' + (isAvailable ? 'tag--success' : '')">{{ isAvailable ? '档期可约' : '请选择时段' }}</text></view>
    <picker class="select-row" mode="date" :value="date" :start="minDate" @change="onDate"><view class="line"><text class="line__title">日期</text><view class="select-row__value"><text>{{ date || '请选择日期' }}</text><view class="control-chevron" aria-hidden="true"></view></view></view></picker>
    <picker class="select-row" mode="time" :value="startTime" start="10:00" end="21:00" @change="onTime"><view class="line"><text class="line__title">开始时间</text><view class="select-row__value"><text>{{ startTime }}</text><view class="control-chevron" aria-hidden="true"></view></view></view></picker>
    <view class="line"><text class="line__title">服务时长</text><view class="stepper"><button @tap="decrement" :disabled="duration <= minHours" aria-label="减少一小时">−</button><text>{{ duration }}小时</text><button @tap="increment" :disabled="duration >= maxHours" aria-label="增加一小时">＋</button></view></view>
    <view class="availability"><view class="availability__bar" :style="'left: ' + barLeft + '%; width: ' + barWidth + '%;'"></view><view class="availability__ticks"><text>10:00</text><text>14:00</text><text>18:00</text><text>22:00</text></view></view>
    <text :class="'field-help ' + (isAvailable ? '' : 'field-error')">营业时段 {{ windowLabel }}。{{ startTime }} 起连续 {{ duration }} 小时；{{ isAvailable ? '当前时间段可约。' : '所选时间包含不可约或已占用时段，请重新选择。' }}</text>
  </view>

  <view class="section">
    <view class="section__head"><text class="section__title">Coser 可约时段</text><text class="caption">{{ windowLabel }}</text></view>
    <view class="booking-dates"><button v-for="(item, index) in bookingDates" :key="item.date" :class="&quot;booking-date &quot; + (date === item.date ? 'booking-date--active' : '')" :data-date="item.date" @tap="selectBookingDate"><text>{{ item.label }}</text><text>{{ item.week }}</text></button></view>
    <view class="booking-hours" aria-label="Coser 24小时档期"><view v-for="(item, index) in hours" :key="item.label" :class="&quot;booking-hour &quot; + (item.open ? 'booking-hour--open' : 'booking-hour--closed') + &quot; &quot; + (item.selected ? 'booking-hour--selected' : '')"><text>{{ item.label }}</text></view></view>
    <view class="calendar-legend"><view><view class="legend-dot legend-dot--open"></view><text>可约</text></view><view><view class="legend-dot legend-dot--selected"></view><text>已选择</text></view><view><view class="legend-dot"></view><text>不可约</text></view></view>
  </view>

  <view class="section">
    <view class="section__head"><text class="section__title">见面地点</text><text class="tag">仅限公共场所</text></view>
    <button class="place" @tap="choosePlace"><view><text>{{ place || '请选择公共场所' }}</text><text class="muted">平台会校验场所类型和营业时段</text></view><view class="control-chevron" aria-hidden="true"></view></button>
    <text v-if="errors.place" class="field-error">{{ errors.place }}</text>
  </view>

  <view id="boundary-section" class="section">
    <view class="section__head"><text class="section__title">相处偏好与边界</text><text class="caption">Coser预设</text></view>
    <button v-for="(item, index) in boundaries" :key="item.text" :class="&quot;boundary-confirm-row &quot; + (item.confirmed ? 'boundary-confirm-row--checked' : '') + &quot; &quot; + (item.invalid ? 'boundary-confirm-row--invalid' : '')" :data-index="index" @tap="toggleBoundary" :aria-pressed="item.confirmed"><view class="boundary-confirm-row__check">{{ item.confirmed ? '✓' : '' }}</view><text>{{ item.text }}</text><text>{{ item.confirmed ? '已确认' : item.invalid ? '请确认' : '点击确认' }}</text></button>
    <view class="notice">现场任何互动都以双方当时明确同意为准，可随时撤回。</view>
  </view>

  <view class="section">
    <view class="section__head"><text class="section__title">委托前确认</text><text class="caption">双方都能看到</text></view>
    <label class="field-label">怎么称呼你 *</label><input class="field" data-field="nickname" :value="nickname" @input="onField" placeholder="例如：小鱼"/><text v-if="errors.nickname" class="field-error">{{ errors.nickname }}</text>
    <label class="field-label">喜欢什么</label><textarea class="field" data-field="likes" :value="likes" @input="onField" placeholder="想体验的互动、偏好的聊天方式"/>
    <label class="field-label">不喜欢或需要避开的内容</label><textarea class="field" data-field="dislikes" :value="dislikes" @input="onField" placeholder="雷点、忌口、身体状况等"/>
    <label class="field-label">肢体接触边界</label><picker :range="boundaryOptions" @change="chooseBoundary"><view class="field picker-field"><text>{{ contactBoundary }}</text><view class="control-chevron control-chevron--down" aria-hidden="true"></view></view></picker>
    <label class="field-label">补充说明</label><textarea class="field" data-field="note" :value="note" @input="onField" placeholder="服装、集合方式等其他需求"/>
  </view>

  <view class="section cost"><view class="line"><text>服务金额</text><text>¥{{ total }}</text></view><view class="line"><text>用户侧平台服务费</text><text>¥0</text></view><view class="line cost__total"><text>合计</text><text class="price">¥{{ total }}</text></view></view>
  <view class="notice">请至少提前24小时预约。订单创建后15分钟内付款；付款后由Coser在2小时内确认。支付功能接入前订单停留在待支付状态。</view>
</view>
<view class="bottom-actions"><button class="btn btn--cta" @tap="submit" :disabled="!isAvailable">提交委托 ¥{{ total }}</button></view>

</template>
<script>
import { get, set } from '../../utils/store'
import { services, mapService } from '../../utils/services'
import { request } from '../../services/api'
import { guardBooking } from '../../utils/eligibility'

const pad = (value) => String(value).padStart(2, '0')
const todayKey = () => {
  const now = new Date()
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}
const hourCovered = (slots, date, hour) => {
  const start = `${date}T${pad(hour)}:00:00`
  const end = `${date}T${pad(hour + 1)}:00:00`
  return slots.some(slot => String(slot.startAt) <= start && String(slot.endAt) >= end)
}
const buildHours = (startTime, duration, date, slots) => {
  const start = Number(String(startTime).split(':')[0])
  return Array.from({ length: 24 }, (_, hour) => ({
    label: pad(hour),
    open: hourCovered(slots, date, hour),
    selected: hour >= start && hour < start + duration
  }))
}

export default {
data() { return {
    service: services[0],
    date: '',
    minDate: todayKey(),
    bookingDates: [],
    slots: [],
    venues: [],
    startTime: '14:00',
    duration: 3,
    minHours: 2,
    maxHours: 8,
    hours: buildHours('14:00', 3, '', []),
    isAvailable: false,
    windowLabel: '10:00–22:00',
    barLeft: 33.3,
    barWidth: 25,
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
  } },
onLoad(options) {
    const service = services.find((item) => item.id === options.id) || services[0]
    this.setData({ service, total: service.price * this.duration, minHours: service.minHours || 2 })
    const id = options.id || service.id
    request(`/v1/services/${encodeURIComponent(id)}`).then(detail => {
      const live = mapService(detail)
      const minHours = Number(detail.minHours || live.minHours || 2)
      const maxHours = Number(detail.maxHours || 8)
      const duration = Math.max(minHours, Math.min(this.duration, maxHours))
      this.setData({
        service: live,
        minHours,
        maxHours,
        duration,
        total: live.price * duration,
        boundaries: (detail.boundaries || []).map(text => ({ text, confirmed: false }))
      })
      return Promise.all([
        request(`/v1/cosers/${encodeURIComponent(id)}/availability`),
        request(`/v1/venues?city=${encodeURIComponent(detail.city)}`)
      ])
    }).then(([availability, venues]) => {
      const slots = availability.slots || []
      const dates = [...new Set(slots.map(slot => String(slot.startAt).slice(0, 10)))].map(date => ({ date, label: date.slice(5).replace('-', '/'), week: '' }))
      this.setData({
        slots,
        bookingDates: dates.slice(0, 14),
        venues,
        windowLabel: availability.windowLabel || '10:00–22:00',
        date: dates.length ? dates[0].date : ''
      }, () => this.refreshSchedule())
    }).catch(err => { this.setData({ isAvailable: false }); uni.showToast({ title: err.message || '服务暂不可用', icon: 'none' }) })
    if (uni.getStorageSync('account_token')) request('/v1/users/me').then(user => this.setData({ nickname: user.nickname || '' })).catch(() => {})
  },
methods: {
refreshSchedule(next = {}) {
    const startTime = next.startTime || this.startTime
    const duration = next.duration || this.duration
    const date = next.date || this.date
    const startHour = Number(String(startTime).split(':')[0])
    const endHour = startHour + duration
    const hours = buildHours(startTime, duration, date, this.slots)
    const selected = hours.filter((item) => item.selected)
    const withinWindow = startHour >= 10 && endHour <= 22 && String(startTime).endsWith(':00')
    const barLeft = ((Math.max(10, startHour) - 10) / 12) * 100
    const barWidth = (duration / 12) * 100
    this.setData({
      ...next,
      hours,
      barLeft,
      barWidth,
      isAvailable: !!date && withinWindow && selected.length === duration && selected.every((item) => item.open),
      total: this.service.price * duration
    })
  },
updateDate(date) {
    this.refreshSchedule({ date })
  },
onDate(event) { this.updateDate(event.detail.value) },
selectBookingDate(event) { this.updateDate(event.currentTarget.dataset.date) },
onTime(event) {
    const value = String(event.detail.value || '')
    const hour = Number(value.slice(0, 2))
    const normalized = `${pad(hour)}:00`
    if (hour < 10 || hour > 21) return uni.showToast({ title: '请选择 10:00–21:00 整点开始', icon: 'none' })
    this.refreshSchedule({ startTime: normalized })
  },
decrement() { if (this.duration > this.minHours) this.refreshSchedule({ duration: this.duration - 1 }) },
increment() { if (this.duration < this.maxHours) this.refreshSchedule({ duration: this.duration + 1 }) },
choosePlace() {
    if (!this.venues.length) return uni.showToast({ title: '暂无可选公共场所', icon: 'none' })
    uni.showActionSheet({ itemList: this.venues.map(item => item.name), success: ({ tapIndex }) => {
      const venue = this.venues[tapIndex]
      this.setData({ place: venue.name, placeDetails: venue, 'errors.place': '' })
    } })
  },
onField(event) { this.setData({ [event.currentTarget.dataset.field]: event.detail.value }) },
chooseBoundary(event) {
    this.setData({ contactBoundary: this.boundaryOptions[event.detail.value] })
  },
toggleBoundary(event) {
    const index = Number(event.currentTarget.dataset.index)
    if (!Number.isInteger(index) || index < 0 || index >= this.boundaries.length) return
    const boundaries = this.boundaries.map((item, itemIndex) => itemIndex === index
      ? { ...item, confirmed: !item.confirmed, invalid: false }
      : item)
    this.setData({ boundaries })
  },
async submit() {
    if (!getApp().globalData.localDevMode && !guardBooking()) return
    const errors = {}
    if (!this.isAvailable) return uni.showToast({ title: '所选时段不可约', icon: 'none' })
    if (!uni.getStorageSync('account_token')) return uni.showModal({ title: '请先登录', content: '使用微信登录后即可提交委托。', confirmText: '去登录', success: ({ confirm }) => { if (confirm) uni.switchTab({ url: '/pages/profile/index' }) } })
    const boundaries = this.boundaries.map((item) => ({ ...item, invalid: !item.confirmed }))
    if (!this.placeDetails || !this.venues.some(item => item.id === this.placeDetails.id)) errors.place = '请选择平台核准的公共场所'
    if (!this.nickname.trim()) errors.nickname = '请告诉Coser如何称呼你'
    this.setData({ boundaries, errors })
    if (boundaries.some((item) => item.invalid)) {
      uni.pageScrollTo({ selector: '#boundary-section', duration: 300 })
      return
    }
    if (Object.keys(errors).length) return uni.showToast({ title: '请完善必填信息', icon: 'none' })
    uni.showLoading({ title: '创建订单' })
    try {
      const quote = await request('/v1/orders/quote', { method: 'POST', data: { serviceId: this.service.id, startAt: `${this.date}T${this.startTime}:00`, durationHours: this.duration } })
      const created = await request('/v1/orders', { method: 'POST', header: { 'Idempotency-Key': `booking-${Date.now()}-${Math.random().toString(36).slice(2)}` }, data: { quoteId: quote.quoteId, venueId: this.placeDetails.id } })
      const id = created.id
      const order = {
        id,
        title: this.service.title,
        coser: this.service.coser,
        coserGender: this.service.gender === '男' ? 'M' : 'F',
        customer: this.nickname.trim(),
        date: `${this.date} ${this.startTime}–${String(Number(this.startTime.slice(0, 2)) + this.duration).padStart(2, '0')}:00`,
        place: this.place,
        city: this.placeDetails.city,
        district: this.placeDetails.district,
        placeDetails: this.placeDetails,
        amount: Number(quote.payableAmount) / 100,
        boundaries: this.boundaries.map((item) => item.text),
        status: 'unpaid',
        statusText: '待支付'
      }
      set('orders', [order, ...get('orders', []).filter((item) => item.id !== id)])
      uni.navigateTo({ url: `/pages/order-detail/index?id=${id}&new=1` })
    } catch (err) { uni.showModal({ title: '创建失败', content: err.message || '请稍后重试', showCancel: false }) }
    finally { uni.hideLoading() }
  },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.service-summary { display: flex; gap: 22rpx; align-items: center; }
.summary-cover { width: 150rpx; height: 150rpx; flex: none; display: flex; align-items: flex-end; padding: 14rpx; box-sizing: border-box; border-radius: 22rpx; background: linear-gradient(145deg,#ede9fe,#ddd6fe); color: rgba(36,29,45,.6); font-size: 42rpx; font-weight: 800; }
.service-summary>view:last-child { flex: 1; min-width: 0; }
.service-summary text { display: block; }
.service-summary .price { margin-top: 8rpx; }
.service-summary .price__unit { display: inline; }
.stepper { display: flex; align-items: center; gap: 18rpx; }
.stepper button { width: 88rpx; height: 88rpx; margin: 0; padding: 0; border: 1rpx solid var(--color-border); border-radius: 50%; background: #fff; color: var(--color-primary); font-size: 38rpx; display: flex; align-items: center; justify-content: center; }
.stepper button[disabled] { opacity: .35; }
.availability { position: relative; height: 76rpx; margin-top: 22rpx; overflow: hidden; border-radius: 16rpx; background: #eceaf0; }
.availability::before { content: ''; position: absolute; left: 8%; right: 8%; top: 14rpx; height: 22rpx; border-radius: 999rpx; background: #ccebdd; }
.availability__bar { position: absolute; z-index: 2; top: 10rpx; height: 30rpx; border-radius: 999rpx; background: var(--color-primary); }
.availability__ticks { position: absolute; left: 8%; right: 8%; bottom: 6rpx; display: flex; justify-content: space-between; color: var(--color-muted); font-size: 22rpx; }
.booking-dates { display: grid; grid-template-columns: repeat(4,1fr); gap: 10rpx; }
.booking-date { min-height: 90rpx; margin: 0; padding: 8rpx 4rpx; border: 1rpx solid var(--color-border); border-radius: 16rpx; background: #fff; color: var(--color-muted); font-size: 22rpx; display: flex; flex-direction: column; justify-content: center; }
.booking-date--active { border-color: var(--color-primary); background: var(--color-primary-soft); color: var(--color-primary-ink); font-weight: 750; box-shadow: inset 0 0 0 1rpx var(--color-primary); }
.booking-date--closed { opacity: .45; }
.booking-hours { margin-top: 20rpx; display: grid; grid-template-columns: repeat(6,1fr); gap: 8rpx; }
.booking-hour { height: 52rpx; display: flex; align-items: center; justify-content: center; border-radius: 11rpx; font-size: 22rpx; }
.booking-hour--open { background: var(--color-primary-soft); color: var(--color-primary); }
.booking-hour--closed { background: #f2f0f3; color: #aaa3ae; }
.booking-hour--selected { background: var(--color-primary); color: #fff; font-weight: 800; }
.calendar-legend { margin-top: 16rpx; display: flex; gap: 20rpx; color: var(--color-muted); font-size: 22rpx; }
.calendar-legend>view { display: flex; align-items: center; gap: 7rpx; }
.legend-dot { width: 16rpx; height: 16rpx; display: inline-block; border-radius: 5rpx; background: #f2f0f3; }
.legend-dot--open { background: var(--color-primary-soft); border: 1rpx solid var(--color-primary); }
.legend-dot--selected { background: var(--color-primary); }
.select-row .line { min-height: 100rpx; }
.select-row__value { min-width: 180rpx; min-height: 64rpx; padding: 0 16rpx; display: flex; align-items: center; justify-content: flex-end; gap: 20rpx; border: 2rpx solid var(--color-border); border-radius: 16rpx; background: var(--color-bg); color: var(--color-primary-ink); font-size: 27rpx; font-weight: 700; }
.place { width: 100%; min-height: 108rpx; margin: 0; padding: 14rpx 0; display: flex; align-items: center; justify-content: space-between; gap: 16rpx; border: 0; background: transparent; color: var(--color-text); text-align: left; font-size: 28rpx; }
.place>view:first-child { flex: 1; min-width: 0; }
.place:active { background: var(--color-primary-soft); }
.place text { display: block; }
.boundary-row { display: flex; flex-wrap: wrap; gap: 12rpx; }
.boundary-row text { padding: 12rpx 18rpx; border-radius: 999rpx; background: #f5f0ff; color: #5b21b6; font-size: 24rpx; }
.boundary-confirm-row { width: 100%; min-height: 88rpx; margin: 10rpx 0 0; padding: 10rpx 0; display: grid; grid-template-columns: 42rpx minmax(0,1fr) auto; align-items: center; gap: 14rpx; border: 0; border-bottom: 1rpx solid var(--color-border); border-radius: 0; background: transparent; color: var(--color-text); text-align: left; font-size: 25rpx; line-height: 1.4; }
.boundary-confirm-row>text:last-child { color: var(--color-muted-soft); font-size: 22rpx; }
.boundary-confirm-row__check { width: 38rpx; height: 38rpx; box-sizing: border-box; border: 2rpx solid #bbb4c1; border-radius: 50%; color: #fff; font-size: 23rpx; line-height: 34rpx; text-align: center; }
.boundary-confirm-row--checked .boundary-confirm-row__check { border-color: var(--color-primary); background: var(--color-primary); }
.boundary-confirm-row--checked>text:last-child { color: var(--color-primary); }
.boundary-confirm-row--invalid { padding-left: 12rpx; padding-right: 12rpx; border: 1rpx solid #e75e73; border-radius: 14rpx; background: #fff2f4; color: #a92f48; }
.boundary-confirm-row--invalid .boundary-confirm-row__check { border-color: #e75e73; }
.boundary-confirm-row--invalid>text:last-child { color: #c43853; font-weight: 700; }
.picker-field { display: flex; justify-content: space-between; align-items: center; gap: 18rpx; background: var(--color-bg); }
.cost { font-variant-numeric: tabular-nums; }
.cost .line { min-height: 76rpx; }
.cost__total { font-size: 32rpx; font-weight: 750; }
.chat-mask { position: fixed; z-index: 90; inset: 0; display: flex; align-items: flex-end; background: rgba(25,20,32,.48); }
.chat-sheet { width: 100%; height: 70vh; padding: 18rpx 28rpx calc(24rpx + env(safe-area-inset-bottom)); box-sizing: border-box; display: flex; flex-direction: column; border-radius: 36rpx 36rpx 0 0; background: #fff; }
.chat-sheet__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20rpx; }
.chat-sheet__title,.chat-sheet__hint { display: block; }
.chat-sheet__title { font-size: 34rpx; font-weight: 800; }
.chat-sheet__hint { margin-top: 4rpx; color: var(--color-muted); font-size: 22rpx; }
.chat-sheet__head button { width: 88rpx; height: 88rpx; margin: 0; padding: 0; border: 0; border-radius: 50%; background: var(--color-soft); color: var(--color-muted); font-size: 38rpx; display: flex; align-items: center; justify-content: center; }
.chat-sheet__messages { flex: 1; min-height: 0; margin: 24rpx 0; }
.chat-bubble { width: fit-content; max-width: 76%; margin: 12rpx 0; padding: 18rpx 22rpx; box-sizing: border-box; border-radius: 24rpx 24rpx 24rpx 6rpx; background: #f1eff5; color: var(--color-text); font-size: 25rpx; }
.chat-bubble--mine { margin-left: auto; border-radius: 24rpx 24rpx 6rpx 24rpx; background: var(--color-primary); color: #fff; }
.urgent-confirm { margin-bottom: 14rpx; padding: 18rpx; display: flex; align-items: center; gap: 16rpx; border-radius: 20rpx; background: var(--color-primary-soft); }
.urgent-confirm>view { flex: 1; }
.urgent-confirm text { display: block; }
.urgent-confirm text:first-child { font-weight: 750; }
.urgent-confirm text:last-child { margin-top: 3rpx; color: var(--color-muted); font-size: 22rpx; }
.urgent-confirm button { min-height: 88rpx; margin: 0; padding: 0 18rpx; border: 0; border-radius: 999rpx; background: var(--color-primary); color: #fff; font-size: 22rpx; }
.urgent-confirm button[disabled] { background: #d6d3e2; color: #514b59; }
.chat-compose { display: flex; gap: 12rpx; }
.chat-compose input { flex: 1; min-width: 0; height: 88rpx; box-sizing: border-box; padding: 0 22rpx; border-radius: 999rpx; background: var(--color-soft); font-size: 25rpx; }
.chat-compose button { width: 112rpx; height: 88rpx; margin: 0; padding: 0; display: flex; align-items: center; justify-content: center; border: 0; border-radius: 999rpx; background: var(--color-primary); color: #fff; font-size: 25rpx; font-weight: 700; line-height: 1; }

</style>
