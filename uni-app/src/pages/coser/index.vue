<template>
<view class="page detail-page">
  <view class="profile-hero">
    <view class="service-head">
      <text class="service-head__eyebrow">角色委托 · {{ service.district }}</text>
      <text class="service-head__title">{{ service.title }}</text>
      <view class="service-head__price"><view><text class="price">¥{{ service.price }}</text><text class="service-head__unit">/小时</text></view><text class="service-head__minimum">每单最低 {{ service.minHours || 2 }} 小时</text></view>
    </view>
    <view class="person-row">
      <view class="avatar">{{ avatar }}</view>
      <view class="person-main">
        <view class="person-name"><text>{{ service.coser }}</text><text v-if="service.verified" class="tag tag--success">真人认证</text></view>
        <view class="person-meta"><text>{{ service.city || service.district }}</text></view>
      </view>
      <view class="profile-actions"><button :class="favorite ? 'is-favorite' : ''" @tap="toggleFavorite" :aria-label="favorite ? '取消收藏' : '收藏'" :aria-pressed="favorite">{{ favorite ? '已收藏' : '收藏' }}</button><button open-type="share" aria-label="分享主页">分享</button></view>
    </view>
    <view class="reputation-heading">信誉表现</view>
    <view class="reputation"><view><text class="reputation__value">★ {{ reputation.score }}</text><text class="reputation__label">{{ reputation.sample ? '示例评分' : '累计交易评分' }} · {{ reputation.scoredOrders }}笔已评分</text></view><view><text class="reputation__value">{{ reputation.incompleteCount }}次</text><text class="reputation__label">应履约未完成</text></view></view>
    <text class="reputation__note">未履约次数仅计平台审核确认属于Coser责任的订单。</text>
  </view>

  <view class="section">
    <view class="section__head"><text class="section__title">关于{{ service.coser }}</text></view>
    <text v-if="isSampleProfile" class="body-copy">长期出演《崩坏：星穹铁道》《原神》等角色，擅长漫展同行、主题合影与轻剧情互动。性格开朗，会提前沟通角色设定与活动流程，希望一起留下自然、有趣的回忆。</text>
    <text v-else class="body-copy">{{ service.description || service.coser + '提供「' + service.title + '」委托。具体细节可在下单前沟通。' }}</text>
  </view>

  <view class="section">
    <view class="section__head"><text class="section__title">作品展示</text></view>
    <view v-if="media.length" class="story-grid"><image v-for="(item,index) in media" :key="index" :src="item.url" mode="aspectFill" style="width:210rpx;height:210rpx;border-radius:16rpx"/></view>
    <view v-if="isSampleProfile" class="story-grid"><view class="story story--a"><view class="story__mark">三月七</view><text>三月七 · 漫展同行</text></view><view class="story story--b"><view class="story__mark story__mark--small">✦</view><text>主题合影</text></view><view class="story story--c"><view class="story__mark story__mark--small">✧</view><text>轻剧情互动</text></view></view>
    <view v-else class="detail-empty">暂无作品展示，可先私信了解造型与作品</view>
  </view>

  <view class="section">
    <view class="section__head"><text class="section__title">已有服装</text><text v-if="isSampleProfile" class="caption">4套</text></view>
    <scroll-view v-if="isSampleProfile" scroll-x class="clothes" enhanced show-scrollbar="false"><view v-for="(item, index) in clothes" :key="item.name" class="cloth"><view class="cloth__cover"><text class="cloth__mark">{{ item.name }}</text></view><text class="cloth__name">{{ item.name }}</text><text class="cloth__detail">{{ item.detail }}</text></view></scroll-view>
    <view v-else class="detail-empty">暂无服装展示，可私信确认可用造型</view>
  </view>

  <view class="section">
    <view class="section__head"><text class="section__title">相处偏好与边界</text></view>
    <view class="boundary-chips"><text v-for="(item, index) in boundaries" :key="item" class="boundary-chip">{{ item }}</text></view>
    <text class="boundary-note">仅限公开场所，不接受酒店、住宅、过夜或订单外要求。下单时将逐条确认，以双方更严格的边界为准。</text>
  </view>

  <view class="section schedule-section">
    <button class="schedule-toggle" @tap="toggleSchedule" :aria-expanded="scheduleOpen">
      <view><text class="schedule-toggle__title">可预约日期</text><text class="schedule-toggle__date">{{ selectedDateKey ? '已选 ' : '' }}{{ selectedDate }}</text></view>
      <view class="schedule-toggle__action"><text>{{ scheduleOpen ? '收起日历' : '展开日历' }}</text><view :class="&quot;control-chevron &quot; + (scheduleOpen ? 'control-chevron--up' : 'control-chevron--down') + &quot; control-chevron--active&quot;" aria-hidden="true"></view></view>
    </button>
    <view v-if="scheduleOpen" class="schedule-content">
      <view class="dates"><button v-for="(item, index) in dates" :key="item.key" :class="&quot;date &quot; + (selectedDateKey === item.key ? 'date--active' : '') + &quot; &quot; + (!item.open ? 'date--closed' : '')" :data-index="index" @tap="selectDate" :disabled="!item.open" :aria-label="(item.full) + &quot;，&quot; + (item.open ? '可约' : '不可约')"><text>{{ item.label }}</text><text class="date__sub">{{ item.open ? '可约' : '不可约' }}</text></button></view>
      <view class="available-copy">按日期展示剩余可约状态（已扣除订单占用）。具体开始时间与服务时长在下单时填写，仅可预约 {{ windowLabel }} 内的时段。</view>
    </view>
  </view>
</view>

<view class="bottom-actions"><button class="btn" @tap="openChat">先咨询</button><button class="btn btn--cta" @tap="book" :disabled="!selectedDateKey">{{ selectedDateKey ? '选择日期下单' : '暂无可约日期' }}</button></view>

</template>
<script>
import { get, set } from '../../utils/store'
import { services, mapService } from '../../utils/services'
import { request } from '../../services/api'
import { guardBooking } from '../../utils/eligibility'

export default {
data() { return {
    serviceId: 'svc-1',
    service: services[0],
    avatar: '梨',
    isSampleProfile: true,
    favorite: false,
    media: [],
    scheduleOpen: false,
    selectedDate: '暂无可约日期',
    selectedDateKey: '',
    windowLabel: '10:00–22:00',
    reputation: { score: '—', scoredOrders: 0, incompleteCount: 0, sample: false },
    dates: [],
    hours: Array.from({ length: 24 }, (_, hour) => ({ label: String(hour).padStart(2, '0'), open: hour >= 10 && hour < 22 })),
    boundaries: ['不接受肢体接触', '轻挽手臂', '不饮酒'],
    clothes: [
      { name: '三月七·巡猎', detail: '全套 · 含假发道具' },
      { name: '三月七·常服', detail: '全套 · 含假发' },
      { name: '芙宁娜', detail: '全套 · 含帽子道具' },
      { name: '流萤', detail: '全套 · 含假发配饰' }
    ]
  } },
onLoad(options) {
    const serviceId = options.id || 'svc-1'
    const service = services.find((item) => item.id === serviceId) || services[0]
    this.setData({ serviceId, service, avatar: service.coser.slice(-1), isSampleProfile: serviceId === 'svc-1', favorite: get('favorites', []).includes(serviceId) })
    this.refreshReputation()
    request(`/v1/services/${encodeURIComponent(serviceId)}`).then(detail => {
      const live = { ...mapService(detail), description: detail.description || '', serviceTerms: detail.serviceTerms || '', minHours: detail.minHours, coserId: detail.coserId }
      this.setData({ service: live, avatar: live.coser.slice(-1), isSampleProfile: false, boundaries: detail.boundaries || [], media: (detail.media || []).map(item => ({ ...item, url: item.url && item.url.startsWith('/') ? `${getApp().globalData.apiBaseUrl}${item.url}` : item.url })) })
      this.refreshReputation()
    }).catch(() => {})
    request(`/v1/cosers/${encodeURIComponent(serviceId)}/availability`).then((availability) => {
      const slots = availability.slots || []
      const days = []
      slots.forEach(slot => {
        const date = String(slot.startAt).slice(0, 10)
        const startHour = Number(String(slot.startAt).slice(11, 13))
        const endHour = Number(String(slot.endAt).slice(11, 13))
        if (endHour <= startHour) return
        if (!days.some(item => item.date === date)) {
          days.push({ date, key: date, label: date.slice(5).replace('-', '/'), sub: '', full: date, open: true })
        }
      })
      this.setData({
        windowLabel: availability.windowLabel || '10:00–22:00',
        dates: days.slice(0, 14),
        selectedDate: days.length ? days[0].full : '暂无可约日期',
        selectedDateKey: days.length ? days[0].key : ''
      })
    }).catch(() => {})
  },
onShow() { if (this.serviceId) this.refreshReputation() },
onShareAppMessage() {
    return { title: this.service.title, path: `/pages/coser/index?id=${this.serviceId}` }
  },
methods: {
refreshReputation() {
    const service = this.service
    const coserId = service.coserId
    if (coserId) {
      request(`/v1/cosers/${encodeURIComponent(coserId)}/reputation`).then((data) => {
        this.setData({ reputation: { score: data.averageScore == null ? '—' : String(data.averageScore), scoredOrders: data.scoredOrders || 0, incompleteCount: data.incompleteCount || 0, sample: false } })
      }).catch(() => {})
      return
    }
    const localId = service.id === 'svc-1' ? 'mine' : service.id
    const reviews = Array.from(new Map(get('orderReviews', []).filter(item => item.coserId === localId && Number(item.score) >= 1 && Number(item.score) <= 5).map(item => [item.orderId, item])).values())
    const cases = get('responsibilityCases', [])
    const incompleteCount = new Set(cases.filter(item => item.coserId === localId && item.status === 'approved' && item.decision === 'coser' && item.fulfillmentIncomplete).map(item => item.orderId)).size
    this.setData({ reputation: { score: reviews.length ? (reviews.reduce((sum, item) => sum + Number(item.score), 0) / reviews.length).toFixed(1) : service.rating ? Number(service.rating).toFixed(1) : '—', scoredOrders: reviews.length, incompleteCount, sample: !reviews.length && !!service.rating } })
  },
toggleFavorite() {
    const favorites = get('favorites', [])
    const next = favorites.includes(this.serviceId) ? favorites.filter((id) => id !== this.serviceId) : [...favorites, this.serviceId]
    set('favorites', next)
    this.setData({ favorite: next.includes(this.serviceId) })
    uni.showToast({ title: this.favorite ? '已收藏' : '已取消', icon: 'none' })
  },
selectDate(event) {
    const item = this.dates[event.currentTarget.dataset.index]
    if (!item.open) return uni.showToast({ title: '当天暂无可约时间', icon: 'none' })
    this.setData({ selectedDate: item.full, selectedDateKey: item.key })
  },
toggleSchedule() { this.setData({ scheduleOpen: !this.scheduleOpen }) },
openChat() { uni.navigateTo({ url: `/pages/chat/index?serviceId=${encodeURIComponent(this.serviceId)}&name=${encodeURIComponent(this.service.coser)}` }) },
book() { if (!this.dates.length) return uni.showToast({ title: '暂无可约时间', icon: 'none' }); if (!getApp().globalData.localDevMode && !guardBooking()) return; uni.navigateTo({ url: `/pages/booking/index?id=${this.serviceId}` }) },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.hero { position: relative; height: 520rpx; overflow: hidden; background: linear-gradient(150deg,#ddd6fe,#e9d5ff); }
.hero__visual text { position: absolute; left: 42rpx; bottom: 42rpx; color: rgba(36,29,45,.62); font-size: 128rpx; line-height: 1; font-weight: 900; letter-spacing: -10rpx; }
.hero__planet { position: absolute; right: -36rpx; top: 42rpx; width: 340rpx; height: 340rpx; border-radius: 50%; background: rgba(255,255,255,.32); box-shadow: -330rpx 340rpx 0 80rpx rgba(255,255,255,.16); }
.hero__tools { position: absolute; right: 24rpx; top: 22rpx; display: flex; gap: 12rpx; }
.hero__tools button { min-height: 88rpx; margin: 0; padding: 0 24rpx; border: 0; border-radius: 999rpx; background: rgba(255,255,255,.94); color: var(--color-text); font-size: 24rpx; }
.detail-page { padding-top: 28rpx; }
.profile-hero { padding: 28rpx; border: 1rpx solid var(--color-border); border-radius: 32rpx; background: #fff; box-shadow: 0 10rpx 28rpx rgba(67,55,91,.07); }
.service-head__eyebrow,.service-head__title { display: block; }
.service-head__eyebrow { color: var(--color-muted); font-size: 23rpx; }
.service-head__title { margin-top: 8rpx; color: var(--color-text); font-size: var(--type-page); line-height: 1.3; font-weight: var(--weight-page); overflow-wrap: anywhere; }
.service-head__price { margin-top: 18rpx; padding-bottom: 24rpx; display: flex; align-items: baseline; justify-content: space-between; gap: 12rpx; border-bottom: 1rpx solid var(--color-border); }
.service-head__price>view { flex: none; white-space: nowrap; }
.service-head__price .price { font-size: 42rpx; line-height: 1; }
.service-head__unit { margin-left: 4rpx; color: var(--color-cta); font-size: 24rpx; font-weight: 700; }
.service-head__minimum { color: var(--color-muted); font-size: 23rpx; text-align: right; }
.person-row { margin-top: 24rpx; display: flex; align-items: center; gap: 16rpx; }
.avatar { width: 76rpx; height: 76rpx; flex: none; display: flex; align-items: center; justify-content: center; border-radius: 24rpx; background: linear-gradient(145deg,var(--color-primary),#a78bfa); color: #fff; font-size: 30rpx; font-weight: 800; }
.person-main { flex: 1; min-width: 0; }
.person-name { display: flex; align-items: center; flex-wrap: wrap; gap: 6rpx 10rpx; font-size: 32rpx; line-height: 1.25; font-weight: 800; }
.person-name .tag { min-height: 40rpx; padding: 0 10rpx; font-size: 22rpx; white-space: nowrap; }
.person-meta { margin-top: 10rpx; display: flex; align-items: center; flex-wrap: wrap; gap: 4rpx 12rpx; color: var(--color-muted); font-size: 22rpx; }
.profile-actions { flex: none; display: flex; gap: 8rpx; }
.profile-actions button { flex: none; width: 100rpx; min-height: 76rpx; box-sizing: border-box; padding: 0; margin: 0; display: flex; align-items: center; justify-content: center; border: 1rpx solid var(--color-border); border-radius: 999rpx; background: #fff; color: var(--color-primary); font-size: 22rpx; line-height: 1.2; font-weight: 700; white-space: nowrap; }
.profile-actions .is-favorite { border-color: #d8b4fe; background: var(--color-cta-soft); color: #6d28d9; }
.reputation-heading { margin-top: 26rpx; color: var(--color-text); font-size: 26rpx; line-height: 1.35; font-weight: 800; }
.reputation { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12rpx; margin-top: 12rpx; }
.reputation>view { min-height: 102rpx; padding: 14rpx 18rpx; box-sizing: border-box; border-radius: 18rpx; background: var(--color-primary-soft); }
.reputation text { display: block; }
.reputation__value { color: var(--color-primary-ink); font-size: 32rpx; font-weight: 800; font-variant-numeric: tabular-nums; }
.reputation__label { margin-top: 4rpx; color: var(--color-muted); font-size: 22rpx; line-height: 1.35; }
.reputation__note { display: block; margin-top: 12rpx; color: var(--color-muted); font-size: 21rpx; line-height: 1.4; }
.detail-empty { min-height: 140rpx; padding: 24rpx; box-sizing: border-box; display: flex; align-items: center; justify-content: center; border-radius: 20rpx; background: var(--color-bg); color: var(--color-muted); font-size: 24rpx; text-align: center; }
.title-row { display: flex; justify-content: space-between; gap: 20rpx; }
.title-row>view:first-child { flex: 1; min-width: 0; }
.detail-title { display: block; font-size: 40rpx; line-height: 1.35; font-weight: 800; }
.identity,.location { margin-top: 16rpx; display: flex; align-items: center; flex-wrap: wrap; gap: 12rpx; color: var(--color-muted); font-size: 25rpx; }
.rate { min-width: 150rpx; text-align: right; }
.rate text { display: block; color: var(--color-muted); font-size: 22rpx; }
.rate .price { color: var(--color-cta); font-size: 38rpx; }
.online { padding-left: 18rpx; position: relative; color: var(--color-success); }
.online::before { content: ''; position: absolute; left: 0; top: 11rpx; width: 10rpx; height: 10rpx; border-radius: 50%; background: var(--color-success); }
.body-copy { color: #403747; font-size: 27rpx; line-height: 1.75; }
.story-grid { margin-top: 22rpx; display: grid; grid-template-columns: 1.25fr .75fr; gap: 12rpx; }
.story { position: relative; min-height: 150rpx; padding: 16rpx; box-sizing: border-box; display: flex; align-items: flex-end; overflow: hidden; border-radius: 22rpx; background: linear-gradient(145deg,#ede9fe,#ddd6fe); font-weight: 700; }
.story>text { position: relative; z-index: 2; padding: 4rpx 8rpx; border-radius: 8rpx; background: rgba(255,255,255,.76); color: var(--color-text); font-size: 23rpx; line-height: 1.3; }
.story--a { grid-row: span 2; min-height: 310rpx; }
.story--b { background: linear-gradient(145deg,#f3e8ff,#ddd6fe); }
.story--c { background: linear-gradient(145deg,#e9e0ff,#cbbaf6); }
.story__mark { position: absolute; top: 42%; left: 50%; width: 100%; transform: translate(-50%,-50%) rotate(-9deg); color: rgba(75,65,125,.5); font-size: 62rpx; font-weight: 900; letter-spacing: 5rpx; text-align: center; }
.story__mark--small { font-size: 100rpx; color: rgba(131,70,125,.35); }
.clothes { width: 100%; white-space: nowrap; }
.cloth { width: 200rpx; margin-right: 16rpx; display: inline-block; vertical-align: top; }
.cloth__cover { position: relative; height: 180rpx; overflow: hidden; border-radius: 20rpx; background: linear-gradient(145deg,#ede9fe,#e9d5ff); }
.cloth:nth-child(2n) .cloth__cover { background: linear-gradient(145deg,#e9f6ef,#bddfda); }
.cloth .cloth__mark { position: absolute; top: 50%; left: 50%; width: 100%; padding: 0 12rpx; box-sizing: border-box; transform: translate(-50%,-50%) rotate(-8deg); color: rgba(75,65,125,.5); font-size: 32rpx; font-weight: 800; white-space: normal; text-align: center; }
.cloth text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cloth__name { margin-top: 10rpx; font-size: 24rpx; font-weight: 700; }
.cloth__detail { margin-top: 3rpx; color: var(--color-muted); font-size: 22rpx; }
.boundary { min-height: 72rpx; display: flex; align-items: center; gap: 16rpx; }
.boundary__check { width: 36rpx; height: 36rpx; border-radius: 50%; background: var(--color-primary-soft); color: var(--color-primary); font-size: 22rpx; line-height: 36rpx; text-align: center; }
.boundary-chips { display: flex; flex-wrap: wrap; gap: 12rpx; }
.boundary-chip { padding: 12rpx 18rpx; border-radius: 999rpx; background: #f5f0ff; color: #5b21b6; font-size: 24rpx; }
.boundary-note { display: block; margin-top: 16rpx; color: var(--color-muted); font-size: 24rpx; line-height: 1.65; }
.schedule-section { padding-top: 8rpx; }
.schedule-toggle { width: 100%; min-height: 106rpx; margin: 0; padding: 12rpx 0; display: flex; align-items: center; justify-content: space-between; gap: 12rpx; border: 0; background: transparent; color: var(--color-text); text-align: left; }
.schedule-toggle>view { flex: 1; min-width: 0; }
.schedule-toggle__title,.schedule-toggle__date { display: block; }
.schedule-toggle__title { font-size: var(--type-section); font-weight: var(--weight-section); }
.schedule-toggle__date { margin-top: 5rpx; color: var(--color-muted); font-size: 23rpx; }
.schedule-toggle__action { flex: none; min-width: 148rpx; min-height: 72rpx; padding: 0 16rpx; display: flex; align-items: center; justify-content: center; gap: 12rpx; border-radius: 18rpx; background: var(--color-primary-soft); color: var(--color-primary-ink); font-size: 24rpx; font-weight: 700; }
.schedule-content { padding-top: 20rpx; border-top: 1rpx solid var(--color-border); }
.available-copy { margin: 20rpx 0 14rpx; padding: 16rpx 18rpx; border-radius: 16rpx; background: var(--color-success-soft); color: var(--color-success); font-size: 23rpx; }
.hour-grid { display: grid; grid-template-columns: repeat(6,1fr); gap: 8rpx; }
.hour { height: 52rpx; display: flex; align-items: center; justify-content: center; border-radius: 12rpx; font-size: 22rpx; }
.hour--open { background: var(--color-primary-soft); color: var(--color-primary); font-weight: 700; }
.hour--closed { background: #f3f1f4; color: #aaa3ae; }
.dates { display: grid; grid-template-columns: repeat(4,1fr); gap: 12rpx; }
.date { min-height: 100rpx; margin: 0; padding: 10rpx 4rpx; border: 1rpx solid #d8d2df; border-radius: 18rpx; background: #fff; color: var(--color-text); font-size: 24rpx; display: flex; flex-direction: column; justify-content: center; }
.date__sub { color: var(--color-success); font-size: 22rpx; }
.date--closed { opacity: .46; }
.date--closed .date__sub { color: var(--color-muted); }
.date--active { border-color: var(--color-primary); background: var(--color-primary-soft); color: var(--color-primary-ink); font-weight: 750; box-shadow: inset 0 0 0 1rpx var(--color-primary); }
.timeline { margin-top: 24rpx; }
.slot { position: relative; min-height: 76rpx; padding-left: 34rpx; display: flex; align-items: center; justify-content: space-between; font-size: 25rpx; }
.slot::before { content: ''; position: absolute; left: 4rpx; top: 29rpx; width: 14rpx; height: 14rpx; border: 5rpx solid var(--color-primary); border-radius: 50%; background: #fff; }
.slot__line { position: absolute; left: 12rpx; top: 48rpx; bottom: -28rpx; width: 2rpx; background: #dcd7e2; }
.slot:last-child .slot__line { display: none; }

</style>
