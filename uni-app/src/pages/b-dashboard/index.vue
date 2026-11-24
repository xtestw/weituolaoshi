<template>
<view class="page dashboard-page">
  <view class="dashboard-hero"><view><text class="dashboard-hero__eyebrow">{{ greeting }}，{{ displayName }}</text><text class="dashboard-hero__title">{{ heroTitle }}</text><text>{{ statusLine }}</text></view><view class="score"><text>{{ averageScore }}</text><text>交易评分</text></view></view>
  <view class="metric-grid"><view><text>{{ averageScore }}</text><text>累计{{ scoredOrders }}笔评分</text></view><view><text>{{ incompleteCount }}</text><text>审核确认未履约</text></view><view><text>{{ weekCompletedCount }}</text><text>本周履约</text></view></view>
  <view class="section"><text class="muted">仅统计平台审核确认属于Coser责任的未完成履约订单；审核中与无责订单不计入。</text></view>
  <view class="section"><view class="section__head"><text class="section__title">快捷管理</text></view><view class="quick-grid"><button @tap="openOrders"><view class="quick-mark">单</view><text>订单</text></button><button @tap="openCalendar"><view class="quick-mark">历</view><text>档期</text></button><button @tap="openPublish"><view class="quick-mark">帖</view><text>帖子</text></button><button @tap="openProfile"><view class="quick-mark">账</view><text>收益</text></button></view></view>
  <view v-if="spotlight" class="section new-order"><view class="section__head"><text class="section__title">{{ spotlight.status === 'awaiting_coser' ? '新委托待确认' : '即将履约' }}</text><button class="section__link" @tap="openOrders">全部订单 ›</button></view><text class="new-order__title">{{ spotlight.title }} <text :class="'tag ' + (spotlight.status === 'awaiting_coser' ? '' : 'tag--success')">{{ spotlightStatusText }}</text></text><text class="muted">{{ spotlight.customer }} · {{ spotlight.timeText }}</text><text class="muted">{{ spotlight.place }} · 实付 ¥{{ spotlight.amountText }}</text><view class="btn-row"><button class="btn btn--small" @tap="contact">私信沟通</button><button v-if="spotlight.status === 'awaiting_coser'" class="btn btn--danger btn--small" @tap="reject">无法接单</button><button v-if="spotlight.status === 'awaiting_coser'" class="btn btn--primary btn--small" @tap="accept">确认接单</button><button v-else class="btn btn--primary btn--small" @tap="openOrders">查看订单</button></view></view>
  <view v-else class="section new-order"><view class="section__head"><text class="section__title">当前委托</text><button class="section__link" @tap="openOrders">全部订单 ›</button></view><text class="muted">暂无待确认或待履约订单</text></view>
  <view class="section risk"><view class="section__head"><text class="section__title">履约安全</text><text class="section__link">查看规则</text></view><view class="line"><text>仅限公共场所</text><text class="tag tag--success">已校验</text></view><view class="line"><text>异常与申诉</text><text class="line__value">随时上报 ›</text></view></view>
  <button class="standalone-entry" @tap="openException"><view class="quick-mark quick-mark--danger">盾</view><view><text>履约异常处理</text><text>用户未到、地点风险、取消责任与争议申诉</text></view><text>›</text></button>
</view>
<button v-if="hasActiveFulfillment" class="emergency-float" @tap="callEmergencyContact" aria-label="紧急联系预设联系人">紧急联系</button>
<b-tabbar selected="dashboard"/>

</template>
<script>
import BTabbar from '../../components/b-tabbar/index.vue'
import { request } from '../../services/api'
import { callEmergencyContact } from '../../utils/emergency'

const statusText = { awaiting_coser: '已付款', confirmed: '已确认', in_service: '履约中' }

function money(cents) {
  return (Number(cents || 0) / 100).toFixed(Number(cents || 0) % 100 ? 2 : 0)
}

function formatRange(startAt, endAt) {
  const start = String(startAt || '').replace('T', ' ')
  const end = String(endAt || '').replace('T', ' ')
  if (!start) return ''
  const date = start.slice(5, 10).replace('-', '月') + '日'
  return `${date} ${start.slice(11, 16)}–${end.slice(11, 16)}`
}

export default {
components: { BTabbar },
data() {
  return {
    displayName: 'Coser',
    verificationStatus: '',
    serviceStatus: '',
    pendingConfirmCount: 0,
    pendingFulfillCount: 0,
    weekCompletedCount: 0,
    averageScore: '—',
    scoredOrders: 0,
    incompleteCount: 0,
    spotlight: null,
    loadError: ''
  }
},
computed: {
  greeting() {
    const hour = new Date().getHours()
    if (hour < 12) return '早上好'
    if (hour < 18) return '下午好'
    return '晚上好'
  },
  heroTitle() {
    if (this.pendingConfirmCount > 0) return `有${this.pendingConfirmCount}笔新委托待确认`
    if (this.pendingFulfillCount > 0) return `今天有${this.pendingFulfillCount}场待履约`
    return '暂无待处理委托'
  },
  statusLine() {
    const verified = this.verificationStatus === 'verified' ? '真人认证已通过' : '认证待完善'
    const open = this.serviceStatus === 'active' ? '营业中' : (this.serviceStatus === 'paused' ? '已暂停接单' : '未营业')
    return `${verified} · ${open}`
  },
  spotlightStatusText() {
    return this.spotlight ? (statusText[this.spotlight.status] || this.spotlight.status) : ''
  },
  hasActiveFulfillment() {
    return this.spotlight && ['confirmed', 'in_service'].includes(this.spotlight.status)
  }
},
onShow() { this.refresh() },
methods: {
  refresh() {
    if (!uni.getStorageSync('account_token')) return
    request('/v1/coser/dashboard').then((data) => {
      const spotlight = data.spotlight ? {
        ...data.spotlight,
        timeText: formatRange(data.spotlight.startAt, data.spotlight.endAt),
        amountText: money(data.spotlight.paidAmount || data.spotlight.payableAmount)
      } : null
      this.setData({
        displayName: data.displayName || 'Coser',
        verificationStatus: data.verificationStatus || '',
        serviceStatus: data.serviceStatus || '',
        pendingConfirmCount: data.pendingConfirmCount || 0,
        pendingFulfillCount: data.pendingFulfillCount || 0,
        weekCompletedCount: data.weekCompletedCount || 0,
        averageScore: data.averageScore == null ? '—' : String(data.averageScore),
        scoredOrders: data.scoredOrders || 0,
        incompleteCount: data.incompleteCount || 0,
        spotlight,
        loadError: ''
      })
    }).catch((error) => {
      this.loadError = error.message || '工作台加载失败'
      uni.showToast({ title: this.loadError, icon: 'none' })
    })
  },
  openOrders() { uni.navigateTo({ url: '/pages/b-orders/index' }) },
  openCalendar() { uni.navigateTo({ url: '/pages/b-calendar/index' }) },
  openProfile() { uni.navigateTo({ url: '/pages/b-profile/index' }) },
  openPublish() { uni.switchTab({ url: '/pages/publish/index' }) },
  callEmergencyContact,
  openException() {
    const reasons = ['用户未到场', '地点存在安全风险', '用户需求超出边界', '取消责任争议', '其他异常']
    uni.showActionSheet({
      itemList: reasons,
      success: ({ tapIndex }) => {
        const orderId = this.spotlight && this.spotlight.id
        const payload = { category: reasons[tapIndex], subject: '履约异常处理', body: reasons[tapIndex] }
        if (orderId) payload.orderId = orderId
        const create = orderId
          ? request(`/v1/orders/${encodeURIComponent(orderId)}/exceptions`, { method: 'POST', data: { reason: reasons[tapIndex] } })
              .then(() => request('/v1/support/tickets', { method: 'POST', data: payload }))
          : request('/v1/support/tickets', { method: 'POST', data: payload })
        create.then(() => uni.showToast({ title: '已创建安全工单', icon: 'none' }))
          .catch((error) => uni.showToast({ title: error.message || '提交失败', icon: 'none' }))
      }
    })
  },
  contact() {
    if (!this.spotlight) return
    uni.navigateTo({ url: '/pages/chat/index?orderId=' + encodeURIComponent(this.spotlight.id) })
  },
  reject() {
    if (!this.spotlight) return
    const id = this.spotlight.id
    const reasons = ['身体不适无法履约', '档期冲突', '服装或道具异常', '需求超出服务边界', '其他原因']
    uni.showActionSheet({
      itemList: reasons,
      success: ({ tapIndex }) => {
        request(`/v1/orders/${encodeURIComponent(id)}/reject`, { method: 'POST', data: { reason: reasons[tapIndex] } })
          .then(() => {
            uni.showModal({ title: '已提交平台审核', content: '原因已记录。只有平台审核确认属于Coser责任且未完成履约后，才会计入首页次数。', showCancel: false })
            this.refresh()
          })
          .catch((error) => uni.showToast({ title: error.message || '拒单失败', icon: 'none' }))
      }
    })
  },
  accept() {
    if (!this.spotlight) return
    uni.showModal({
      title: '确认接单',
      content: '确认后双方进入待履约状态，档期将被锁定，帖子核心信息在订单结束前不能修改。',
      success: ({ confirm }) => {
        if (!confirm) return
        request(`/v1/orders/${encodeURIComponent(this.spotlight.id)}/confirm`, { method: 'POST' })
          .then(() => this.refresh())
          .catch((error) => uni.showToast({ title: error.message || '接单失败', icon: 'none' }))
      }
    })
  },
  setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.dashboard-page { padding-top: 24rpx; }
.dashboard-hero { min-height: 230rpx; padding: 34rpx; display: flex; justify-content: space-between; gap: 20rpx; border-radius: 34rpx; background: linear-gradient(135deg,var(--color-primary),#8b5cf6); color: #fff; }
.dashboard-hero>view:first-child { flex: 1; min-width: 0; }
.dashboard-hero text { display: block; }
.dashboard-hero__eyebrow { opacity: .82; font-size: 24rpx; }
.dashboard-hero__title { margin: 10rpx 0; font-size: var(--type-page); line-height: 1.25; font-weight: var(--weight-page); }
.dashboard-hero view>text:last-child { opacity: .8; font-size: 22rpx; }
.score { width: 100rpx; height: 100rpx; flex: none; display: flex; flex-direction: column; align-items: center; justify-content: center; border-radius: 50%; background: rgba(255,255,255,.16); }
.score text:first-child { font-size: 32rpx; font-weight: 800; }
.score text:last-child { font-size: 22rpx; }
.metric-grid { margin-top: -34rpx; padding: 28rpx 18rpx; display: grid; grid-template-columns: repeat(3,1fr); border-radius: 30rpx; background: #fff; box-shadow: 0 12rpx 30rpx rgba(55,42,77,.1); }
.metric-grid view { border-right: 1rpx solid var(--color-border); text-align: center; }
.metric-grid view:last-child { border: 0; }
.metric-grid text { display: block; }
.metric-grid text:first-child { font-size: 32rpx; font-weight: 800; }
.metric-grid text:last-child { color: var(--color-muted); font-size: var(--type-caption); }
.quick-grid { display: grid; grid-template-columns: repeat(4,1fr); gap: 12rpx; }
.quick-grid button { min-height: 126rpx; margin: 0; padding: 8rpx; border: 0; background: transparent; color: var(--color-muted); font-size: 23rpx; display: flex; flex-direction: column; align-items: center; gap: 10rpx; }
.quick-mark { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; border-radius: 20rpx; background: var(--color-primary-soft); color: var(--color-primary); font-weight: 800; }
.new-order__title { display: block; margin-bottom: 8rpx; font-size: var(--type-card); font-weight: var(--weight-card); }
.new-order>.muted { display: block; margin-top: 4rpx; }
.request { margin: 20rpx 0; padding: 18rpx; border-radius: 18rpx; background: var(--color-bg); color: var(--color-muted); font-size: 24rpx; }
.standalone-entry { width: 100%; min-height: 120rpx; margin: 22rpx 0 0; padding: 20rpx 24rpx; display: flex; align-items: center; gap: 18rpx; border: 1rpx solid var(--color-border); border-radius: 28rpx; background: #fff; color: var(--color-text); text-align: left; box-shadow: 0 8rpx 24rpx rgba(55,42,77,.05); }
.standalone-entry>view:nth-child(2) { flex: 1; }
.standalone-entry>view:nth-child(2) text { display: block; }
.standalone-entry>view:nth-child(2) text:first-child { font-size: var(--type-card); font-weight: var(--weight-card); }
.standalone-entry>view:nth-child(2) text:last-child { margin-top: 4rpx; color: var(--color-muted); font-size: var(--type-caption); }
.quick-mark--danger { background: var(--color-danger-soft); color: var(--color-danger); }
.emergency-float { position: fixed; z-index: 999; right: 24rpx; bottom: calc(150rpx + env(safe-area-inset-bottom)); min-width: 176rpx; height: 88rpx; margin: 0; padding: 0 20rpx; display: flex; align-items: center; justify-content: center; border: 2rpx solid #fff; border-radius: 999rpx; background: #c9384f; color: #fff; font-size: 26rpx; font-weight: 800; box-shadow: 0 10rpx 32rpx rgba(114,25,45,.3); }

</style>
