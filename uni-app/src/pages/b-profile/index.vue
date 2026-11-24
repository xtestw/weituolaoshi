<template>
<view class="page b-profile-page">
  <view class="person"><view class="avatar"><image v-if="accountAvatar" :src="accountAvatar" mode="aspectFill"/><text v-else>{{ accountInitial }}</text></view><view><text class="name">{{ accountName }} <text v-if="coserVerified" class="tag tag--success">Coser 已认证</text></text><text class="muted">{{ identityLabel }}</text><button class="account-edit" @tap="editAccount">{{ loggedIn ? '编辑个人资料 ›' : '去登录 ›' }}</button></view></view>
  <text v-if="accountError" class="account-error">{{ accountError }}</text>
  <view class="section"><view class="section__head"><text class="section__title">交易表现</text></view><view class="line"><text>累计交易评分</text><text class="line__value">{{ scoreLabel }}</text></view><view class="line"><text>应履约未完成</text><text class="line__value">{{ incompleteLabel }}</text></view><text class="caption">未履约次数仅计平台审核确认属于 Coser 责任的订单。</text></view>
  <view class="section settlement">
    <view class="section__head"><text class="section__title">收款与结算</text></view>
    <text class="settlement__note">双方确认履约完成、订单无争议后，平台按规则结算至微信。</text>
    <view class="settlement-grid"><view><text>{{ pendingAmount }}</text><text>待结算</text></view><view><text>{{ monthSettledAmount }}</text><text>本月已结算</text></view><view><text>{{ monthOrderCount }}</text><text>本月订单</text></view></view>
  </view>
  <view class="section"><view class="section__head"><text class="section__title">平台费用与结算</text></view><view class="line"><text>平台安全保障费用</text><text class="line__value">成交金额{{ platformFeePercent }}%</text></view><view class="line"><text>结算规则</text><text class="line__value">完成后{{ settlementHoldHours }}小时无争议</text></view></view>
  <view class="section menu"><button @tap="openPublish"><text>主页与帖子管理</text><text>›</text></button><button @tap="openIncome"><text>收入与结算明细</text><text>›</text></button><button @tap="openSafety"><text>履约安全与规则</text><text>›</text></button><button @tap="openSupport"><text>申诉与客服</text><text>›</text></button></view>
  <button class="btn customer-switch" @tap="switchCustomer">切换到普通用户</button>
</view><b-tabbar selected="profile"/>

</template>
<script>
import BTabbar from '../../components/b-tabbar/index.vue'
import { set } from '../../utils/store'
import { currentUser, request } from '../../services/api'

function money(cents) {
  if (cents == null) return '—'
  const value = Number(cents) / 100
  return '¥' + value.toFixed(value % 1 ? 2 : 0)
}

export default {
components: { BTabbar },
data() {
  return {
    loggedIn: false,
    accountName: '未登录',
    accountAvatar: '',
    accountInitial: '我',
    coserVerified: false,
    identityLabel: '登录后查看个人资料',
    accountError: '',
    averageScore: null,
    scoredOrders: 0,
    incompleteCount: 0,
    pendingAmount: '—',
    monthSettledAmount: '—',
    monthOrderCount: '—',
    platformFeePercent: 5,
    settlementHoldHours: 48,
    settlements: []
  }
},
computed: {
  scoreLabel() {
    if (this.averageScore == null) return '暂未提供'
    return `${this.averageScore} · ${this.scoredOrders}笔`
  },
  incompleteLabel() {
    return this.loggedIn ? `${this.incompleteCount}次` : '—'
  }
},
async onShow() {
  this.accountError = ''
  this.showAccount(uni.getStorageSync('account_token') ? uni.getStorageSync('account_user') : null)
  try {
    const user = await currentUser()
    this.showAccount(user)
    if (!user) return
    const identity = await request('/v1/identity/me')
    this.identityLabel = identity.status === 'verified' ? (identity.faceBypassed ? '测试认证已通过' : '实名认证已通过') : '尚未完成实名认证'
    await this.loadWorkbenchStats()
  } catch (error) {
    if (error.statusCode === 401) this.showAccount(null)
    this.identityLabel = this.loggedIn ? '认证状态暂不可用' : '登录后查看个人资料'
    this.accountError = error.statusCode === 401 ? '登录已过期，请重新登录' : '资料同步失败，请稍后重试'
  }
},
methods: {
showAccount(user) {
  this.loggedIn = !!user
  this.accountName = user ? (user.nickname || '微信用户') : '未登录'
  this.accountInitial = user ? this.accountName.slice(0, 1) : '我'
  const avatar = user && user.avatarUrl || ''
  this.accountAvatar = avatar.startsWith('/') ? `${getApp().globalData.apiBaseUrl}${avatar}` : avatar
  this.coserVerified = !!user && user.role === 'coser'
  this.identityLabel = user ? '认证状态加载中…' : '登录后查看个人资料'
},
async loadWorkbenchStats() {
  const [reputation, summary] = await Promise.all([
    request('/v1/cosers/me/reputation'),
    request('/v1/cosers/me/settlements/summary')
  ])
  this.averageScore = reputation.averageScore
  this.scoredOrders = reputation.scoredOrders || 0
  this.incompleteCount = reputation.incompleteCount || 0
  this.pendingAmount = money(summary.pendingAmount)
  this.monthSettledAmount = money(summary.monthSettledAmount)
  this.monthOrderCount = String(summary.monthOrderCount || 0)
  this.platformFeePercent = Math.round((summary.platformFeeBps || 500) / 100)
  this.settlementHoldHours = summary.settlementHoldHours || 48
},
editAccount() {
  if (this.loggedIn) return uni.navigateTo({ url: '/pages/profile-edit/index' })
  set('role', 'customer')
  uni.switchTab({ url: '/pages/profile/index' })
},
switchCustomer() { set('role', 'customer'); uni.switchTab({ url: '/pages/profile/index' }) },
openPublish() { uni.switchTab({ url: '/pages/publish/index' }) },
openIncome() {
  request('/v1/cosers/me/settlements').then((rows) => {
    this.settlements = rows || []
    if (!this.settlements.length) {
      return uni.showModal({
        title: '收入与结算',
        content: `双方确认履约完成且无争议后，平台按规则结算至微信。当前暂无结算记录。完成后${this.settlementHoldHours}小时无争议自动结算。`,
        showCancel: false
      })
    }
    const lines = this.settlements.slice(0, 5).map((item) => {
      const amount = money(item.payableAmount)
      const status = item.status === 'settled' ? '已结算' : (item.status === 'pending' ? '待结算' : item.status)
      return `${item.title || item.orderId} · ${amount} · ${status}`
    }).join('\n')
    uni.showModal({ title: '收入与结算明细', content: lines, showCancel: false })
  }).catch((error) => uni.showToast({ title: error.message || '明细加载失败', icon: 'none' }))
},
openSafety() { uni.navigateTo({ url: '/pages/safety/index' }) },
openSupport() {
  uni.showModal({
    title: '申诉与客服',
    content: '提交客服工单后，平台将按工单处理争议与申诉。',
    confirmText: '提交工单',
    success: ({ confirm }) => {
      if (!confirm) return
      request('/v1/support/tickets', {
        method: 'POST',
        data: { category: '申诉与客服', subject: '申诉与客服', body: '用户从工作台发起客服/申诉请求' }
      }).then(() => uni.showToast({ title: '工单已提交', icon: 'none' }))
        .catch((error) => uni.showToast({ title: error.message || '提交失败', icon: 'none' }))
    }
  })
},
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.customer-switch { width: 100%; margin-top: 24rpx; }
.b-profile-page { padding-top: 26rpx; }
.person { padding: 20rpx; display: flex; gap: 20rpx; align-items: center; }
.avatar { width: 112rpx; height: 112rpx; flex: none; display: flex; align-items: center; justify-content: center; border-radius: 38rpx; background: linear-gradient(145deg,var(--color-primary),#a78bfa); color: #fff; font-size: 40rpx; font-weight: 800; }
.avatar image { width: 100%; height: 100%; border-radius: inherit; }
.account-edit { margin: 8rpx 0 0; padding: 0; background: transparent; color: var(--color-primary); font-size: 24rpx; text-align: left; }
.account-error { display: block; color: #b42339; margin: 16rpx 20rpx; font-size: 24rpx; }
.person>view:last-child { flex: 1; min-width: 0; }
.person text { display: block; }
.name { font-size: var(--type-section); font-weight: var(--weight-section); }
.name .tag { display: inline-flex; font-size: 22rpx; vertical-align: middle; }
.settlement__note { display: block; margin-bottom: 24rpx; color: var(--color-muted); font-size: 25rpx; line-height: 1.5; }
.settlement-grid { display: grid; grid-template-columns: repeat(3,1fr); }
.settlement-grid view { border-right: 1rpx solid var(--color-border); text-align: center; }
.settlement-grid view:last-child { border: 0; }
.settlement-grid text { display: block; }
.settlement-grid text:first-child { font-weight: 750; }
.settlement-grid text:last-child { color: var(--color-muted); font-size: var(--type-caption); }
.menu button { width: 100%; min-height: 96rpx; margin: 0; padding: 0; display: flex; align-items: center; justify-content: space-between; border: 0; border-bottom: 1rpx solid var(--color-border); background: transparent; color: var(--color-text); font-size: 27rpx; }
.menu button:last-child { border: 0; }
.menu button text:last-child { color: var(--color-muted); }

</style>
