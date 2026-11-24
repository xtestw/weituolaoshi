<template>
<view class="safe-top profile-head"><view class="profile-head__bar"><text>我的</text></view><view class="person"><view class="avatar"><image v-if="accountAvatar" :src="accountAvatar" mode="aspectFill" aria-label="账号头像"/><text v-else>{{ accountInitial }}</text></view><view><view class="person__identity"><text v-if="loggedIn" class="person__name">{{ accountName }}</text><text v-else class="person__name person__name--login" role="button" aria-label="登录" @tap="loginAccount">{{ loginLoading ? '登录中…' : '登录 ›' }}</text><view v-if="loggedIn && coserVerified && role === 'coser'" class="coser-status" aria-label="Coser已认证"><text aria-hidden="true">✓</text></view></view><text class="muted">{{ loggedIn ? (verified ? (eligible ? '已成年 · 可下单' : '已核验 · 仅可浏览') : '未实名认证 · 可浏览') : '登录后可同步账号与委托' }}</text><button v-if="loggedIn" class="profile-edit-trigger" @tap="editProfile">编辑个人资料 ›</button></view></view><text v-if="authError" class="profile-auth-error">{{ authError }}</text></view>
<view class="page profile-page">
  <template v-if="role === 'customer'">
    <view class="section commission"><view class="section__head"><text class="section__title">我的委托</text><button @tap="openOrders">全部委托 ›</button></view><view class="status-grid"><button data-tab="confirm" @tap="openOrders"><view class="status-icon status-icon--clock"></view><text>待确认</text><text v-if="confirmCount" class="count">{{ confirmCount }}</text></button><button data-tab="pay" @tap="openOrders"><view class="status-icon status-icon--card"></view><text>待支付</text><text v-if="payCount" class="count">{{ payCount }}</text></button><button data-tab="service" @tap="openOrders"><view class="status-icon status-icon--calendar"></view><text>待履约</text><text v-if="serviceCount" class="count">{{ serviceCount }}</text></button><button data-tab="review" @tap="openOrders"><view class="status-icon status-icon--heart"></view><text>待评价</text><text v-if="reviewCount" class="count">{{ reviewCount }}</text></button></view></view>
    <button class="entry" @tap="openFavorites"><view class="entry__mark">收</view><view><text>我的收藏</text><text>{{ favorites }}个心仪委托</text></view><text>›</text></button>
    <button class="entry" @tap="openCommunityPosts"><view class="entry__mark">帖</view><view><text>我的社区帖子</text><text>发布与管理普通用户内容</text></view><text>›</text></button>
    <view class="section menu"><button @tap="openCoser"><text>我是 Coser</text><text>进入 Coser 端 ›</text></button><button @tap="openVerification"><text>实名认证</text><text>{{ verified ? '已完成' : '去认证' }} ›</text></button><button @tap="openSafety"><text>安全中心</text><text>{{ emergencyContactSet ? '已设置紧急联系人' : '设置紧急联系人' }} ›</text></button><button @tap="openHelp"><text>帮助与客服</text><text>›</text></button></view>
  </template>
  <template v-else>
    <view v-if="!coserVerified" class="section menu"><button @tap="openCoserVerification"><text>成为 Coser</text><text>完成认证 ›</text></button><button @tap="openCoserPublish"><text>发布服务</text><text>认证后可发布 ›</text></button><button @tap="backToCustomer"><text>返回用户菜单</text><text>›</text></button></view>
    <template v-else>
    <view class="section coser-performance"><view class="section__head"><text class="section__title">交易表现</text></view><view class="coser-performance__grid"><view><text>{{ averageScore }}</text><text>累计交易评分 · {{ scoredOrders }}笔</text></view><view><text>{{ coserIncompleteCount }}</text><text>应履约未完成</text></view></view><text class="caption">未完成次数仅统计平台审核确认属于Coser责任的订单。</text></view>
    <view class="section coser-orders">
      <view class="section__head"><text class="section__title">我的接单</text><button class="section-action" data-filter="all" @tap="openBOrders">全部订单 ›</button></view>
      <view class="coser-order-states">
        <button data-filter="pending" @tap="openBOrders"><text :class="&quot;coser-order-states__count &quot; + (bPendingCount ? 'coser-order-states__count--alert' : '')">{{ bPendingCount }}</text><text>待确认</text></button>
        <button data-filter="service" @tap="openBOrders"><text class="coser-order-states__count">{{ bServiceCount }}</text><text>待履约</text></button>
        <button data-filter="done" @tap="openBOrders"><text class="coser-order-states__count">{{ bDoneCount }}</text><text>已完成</text></button>
      </view>
    </view>
    <view class="section coser-schedule">
      <view class="section__head"><text class="section__title">我的档期</text><button class="section-action" @tap="openBCalendar">调整档期 ›</button></view>
      <text class="coser-schedule__month">{{ scheduleMonth }}</text>
      <view class="coser-schedule__week"><text>一</text><text>二</text><text>三</text><text>四</text><text>五</text><text>六</text><text>日</text></view>
      <view class="coser-schedule__grid"><view v-for="(item, index) in schedulePreview" :key="item.key" :class="'coser-schedule__day ' + (item.blank ? 'coser-schedule__day--blank' : item.occupied ? 'coser-schedule__day--occupied' : item.open ? 'coser-schedule__day--open' : '') + ' ' + (item.today ? 'coser-schedule__day--today' : '')"><text v-if="!item.blank">{{ item.date }}</text><view v-if="!item.blank" class="coser-schedule__dot"></view></view></view>
      <view class="coser-schedule__legend"><view class="coser-schedule__legend-item"><view class="coser-schedule__legend-open"></view><text>可约</text></view><view class="coser-schedule__legend-item"><view class="coser-schedule__legend-occupied"></view><text>已约</text></view><view class="coser-schedule__legend-item"><view class="coser-schedule__legend-closed"></view><text>不可约</text></view></view>
    </view>
    <view class="section coser-settlement">
      <view class="section__head"><text class="section__title">结算</text></view>
      <view class="line"><text>待结算</text><text class="line__value">¥1,742</text></view>
      <view class="line"><text>本月已结算</text><text class="line__value">¥5,920</text></view>
      <view class="line"><text>收款方式</text><text class="line__value">微信收款</text></view>
    </view>
    <view class="section menu"><button @tap="backToCustomer"><text>返回用户菜单</text><text>›</text></button></view>
    </template>
  </template>
  <button v-if="loggedIn" class="profile-logout" :loading="logoutLoading" :disabled="logoutLoading" @tap="exitAccount">退出登录</button>
</view>

</template>
<script>
import { get, set } from '../../utils/store'
import { request, wechatLogin, currentUser, logout } from '../../services/api'
import { loadOrders, normalizeOrder } from '../../utils/order-api'
export default {
data() { return { role: 'customer', verified: false, genderLabel: '', coserVerified: false, favorites: 0, emergencyContactSet: false, confirmCount: 0, payCount: 0, serviceCount: 0, reviewCount: 0, bPendingCount: 0, bServiceCount: 0, bDoneCount: 0, averageScore: '—', scoredOrders: 0, coserIncompleteCount: 0, schedulePreview: [], loggedIn: false, loginLoading: false, logoutLoading: false, accountName: '登录', accountInitial: '我', accountAvatar: '', authError: '', profileEditing: false, profileSaving: false, draftNickname: '', draftAvatar: '' } },
onShow() {
    uni.showTabBar()
    const cachedUser = uni.getStorageSync('account_user')
    const coserVerified = !!uni.getStorageSync('account_token') && cachedUser && cachedUser.role === 'coser'
    const reviews = Array.from(new Map(get('orderReviews', []).filter((item) => item.coserId === 'mine' && Number(item.score) >= 1 && Number(item.score) <= 5).map((item) => [item.orderId, item])).values())
    const cases = get('responsibilityCases', [])
    const averageScore = reviews.length ? (reviews.reduce((sum, item) => sum + Number(item.score), 0) / reviews.length).toFixed(1) : '—'
    const coserIncompleteCount = new Set(cases.filter((item) => item.coserId === 'mine' && item.status === 'approved' && item.decision === 'coser' && item.fulfillmentIncomplete).map((item) => item.orderId)).size
    const today = new Date()
    const year = today.getFullYear()
    const month = today.getMonth() + 1
    const leading = (new Date(year, month - 1, 1).getDay() + 6) % 7
    const schedulePreview = Array.from({ length: leading }, (_, index) => ({ key: `blank-${index}`, blank: true }))
    for (let date = 1; date <= new Date(year, month, 0).getDate(); date++) {
      const key = `${year}-${String(month).padStart(2, '0')}-${String(date).padStart(2, '0')}`
      schedulePreview.push({ key, date, open: false, today: date === today.getDate() })
    }
    const role = 'customer'
    set('role', 'customer')
    this.setData({ role, verified: false, eligible: false, coserVerified, favorites: get('favorites', []).length, emergencyContactSet: !!get('emergencyContact', null), confirmCount: 0, payCount: 0, serviceCount: 0, reviewCount: 0, bPendingCount: 0, bServiceCount: 0, bDoneCount: 0, averageScore, scoredOrders: reviews.length, coserIncompleteCount, schedulePreview, scheduleMonth: `${year}年${month}月` })
    this.showAccount(uni.getStorageSync('account_token') ? uni.getStorageSync('account_user') : null)
    if (uni.getStorageSync('account_token')) {
      this.syncOrderBadges()
      currentUser().then(user => {
        this.showAccount(user)
        this.syncIdentity()
        if (user.role === 'coser') {
          this.syncSchedule()
          this.syncCoserOrderBadges()
        }
      }).catch(error => {
        if (error.statusCode === 401) this.showAccount(null)
        else this.setData({ authError: '账号暂时无法同步，请检查网络后重试' })
      })
    }
  },
methods: {
openCommunityPosts() { if (!this.loggedIn) return this.loginAccount(); uni.navigateTo({ url: '/pages/community-mine/index' }) },
needsProfileSetup(user) { return !!user && (!user.avatarUrl || !user.nickname || user.nickname === '微信用户') },
openProfileSetup() { uni.navigateTo({ url: '/pages/profile-edit/index?firstLogin=1' }) },
showAccount(user) {
    const name = user && user.nickname ? user.nickname : '微信用户'
    const avatar = user && user.avatarUrl ? user.avatarUrl : ''
    const coserVerified = !!user && user.role === 'coser'
    const role = 'customer'
    this.setData({ loggedIn: !!user, coserVerified, role, accountName: user ? name : '登录', accountInitial: user ? name.slice(0, 1) : '我', accountAvatar: avatar && avatar.startsWith('/') ? `${getApp().globalData.apiBaseUrl}${avatar}` : avatar, authError: '' })
  },
editProfile() { if (this.loggedIn) uni.navigateTo({ url: '/pages/profile-edit/index' }) },
syncIdentity() {
    return request('/v1/identity/me').then(identity => {
      const verified = identity.status === 'verified'
      this.setData({ verified, eligible: verified && identity.adult === true && (identity.genderEligible === true || identity.gender === 'F') })
    }).catch(error => {
      if (error.statusCode !== 401) this.setData({ authError: '实名状态暂时无法同步，请稍后重试' })
    })
  },
syncOrderBadges() {
    return loadOrders().then(orders => {
      this.setData({
        confirmCount: orders.filter(item => item.status === 'paid_pending_confirm').length,
        payCount: orders.filter(item => item.status === 'unpaid').length,
        serviceCount: orders.filter(item => item.status === 'service' || item.status === 'in_service').length,
        reviewCount: orders.filter(item => item.status === 'completed').length
      })
    }).catch(() => {})
  },
syncCoserOrderBadges() {
    return request('/v1/coser/orders').then(rows => {
      const orders = rows.map(normalizeOrder)
      this.setData({
        bPendingCount: orders.filter(item => item.status === 'paid_pending_confirm').length,
        bServiceCount: orders.filter(item => item.status === 'service' || item.status === 'in_service').length,
        bDoneCount: orders.filter(item => ['completed', 'cancelled', 'cancel_pending'].includes(item.status)).length
      })
    }).catch(() => this.setData({ bPendingCount: 0, bServiceCount: 0, bDoneCount: 0 }))
  },
syncSchedule() {
    const today = new Date()
    const month = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`
    return request(`/v1/cosers/me/availability?month=${month}`).then(result => {
      const byDate = new Map((result.days || []).map(day => [day.date, day]))
      this.setData({
        schedulePreview: this.schedulePreview.map(day => {
          if (day.blank) return day
          const info = byDate.get(day.key)
          const open = !!(info && info.open)
          const occupied = !!(info && info.status === 'occupied')
          return { ...day, open, occupied, label: occupied ? '已约' : (open ? '可约' : '不可约') }
        })
      })
    }).catch(() => this.setData({ authError: '档期暂时无法同步，请稍后重试' }))
  },
async loginAccount() {
    if (this.loginLoading || this.loggedIn) return
    this.setData({ loginLoading: true, authError: '' })
    try {
      const user = await wechatLogin()
      this.showAccount(user)
      if (this.needsProfileSetup(user)) {
        this.openProfileSetup()
        return user
      }
      await this.syncIdentity()
      await this.syncOrderBadges()
      if (user.role === 'coser') {
        await this.syncSchedule()
        await this.syncCoserOrderBadges()
      }
      return user
    }
    catch (error) { this.setData({ authError: error.message === 'API_NOT_CONFIGURED' ? '服务尚未配置，请联系平台' : (error.message || '登录失败，请重试') }); return null }
    finally { this.setData({ loginLoading: false }) }
  },
exitAccount() {
    if (this.logoutLoading) return
    uni.showModal({ title: '退出登录', content: '退出后需重新使用微信登录。', success: async ({ confirm }) => {
      if (!confirm) return
      this.setData({ logoutLoading: true, authError: '' })
      try {
        await logout()
        set('role', 'customer')
        this.showAccount(null)
        this.setData({ role: 'customer', coserVerified: false, verified: false, eligible: false, profileEditing: false, draftAvatar: '', confirmCount: 0, payCount: 0, serviceCount: 0, reviewCount: 0, bPendingCount: 0, bServiceCount: 0, bDoneCount: 0 })
        uni.showToast({ title: '已退出登录', icon: 'success' })
      }
      catch (error) { this.setData({ authError: error.message || '退出失败，请重试' }) }
      finally { this.setData({ logoutLoading: false }) }
    } })
  },
openCoser() { set('role', 'coser'); uni.navigateTo({ url: '/pages/b-dashboard/index' }) },
backToCustomer() { set('role', 'customer'); this.setData({ role: 'customer' }) },
openCoserVerification() { uni.navigateTo({ url: '/pages/verify/index?returnTo=publish&mode=coser' }) },
openCoserPublish() { uni.switchTab({ url: '/pages/publish/index' }) },
setRole(event) { if (!this.loggedIn) return; const role = event.currentTarget.dataset.role; set('role', role); this.setData({ role }) },
openOrders(event) {
    const tab = event?.currentTarget?.dataset?.tab
    uni.navigateTo({ url: tab ? `/pages/orders/index?tab=${tab}` : '/pages/orders/index' })
  },
openVerify() { uni.navigateTo({ url: '/pages/verify/index' }) },
openVerification() { this.verified ? this.openSafety() : this.openVerify() },
openBOrders(event) {
    if (!this.coserVerified) return
    const filter = event.currentTarget.dataset.filter || 'all'
    uni.navigateTo({ url: `/pages/b-orders/index?filter=${filter}` })
  },
openBCalendar() {
    if (this.coserVerified) uni.navigateTo({ url: '/pages/b-calendar/index' })
  },
openFavorites() { uni.navigateTo({ url: '/pages/favorites/index' }) },
openHelp() { uni.showModal({ title: '帮助与客服', content: '正式上线时接入微信客服与平台工单。', showCancel: false }) },
openSafety() { uni.navigateTo({ url: '/pages/safety/index' }) },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.profile-head { padding: calc(env(safe-area-inset-top) + 88rpx) 28rpx 30rpx; background: linear-gradient(160deg,var(--color-primary-soft),var(--color-cta-soft)); }
.profile-head__bar { position: relative; height: 88rpx; display: flex; align-items: center; justify-content: space-between; }
.profile-head__bar>text { font-size: var(--type-page); line-height: 1.25; font-weight: var(--weight-page); }
.profile-logout { width: 100%; min-height: 88rpx; margin: 32rpx 0 0; display: flex; align-items: center; justify-content: center; border: 1rpx solid var(--color-border); border-radius: 24rpx; background: var(--color-surface); color: var(--color-muted); font-size: 28rpx; font-weight: 650; }
.profile-logout::after { border: 0; }
.profile-logout:active { background: var(--color-primary-soft); }
.profile-logout[disabled] { opacity: .55; }
.person { display: flex; align-items: center; gap: 20rpx; }
.avatar { width: 112rpx; height: 112rpx; flex: none; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: linear-gradient(145deg,var(--color-primary),#a78bfa); color: #fff; font-size: 40rpx; font-weight: 800; overflow: hidden; }
.avatar image { width: 112rpx; height: 112rpx; border-radius: 50%; }
.profile-edit-trigger { width: auto; min-height: 44rpx; margin: 4rpx 0 0; padding: 0; text-align: left; background: transparent; color: var(--color-primary); font-size: 23rpx; line-height: 1.4; }
.profile-edit-trigger::after { border: 0; }
.profile-avatar-picker { width: 112rpx; height: 112rpx; margin: 16rpx 0; padding: 0; border-radius: 50%; overflow: hidden; background: var(--color-primary); color: #fff; font-size: 22rpx; }
.profile-avatar-picker image { width: 112rpx; height: 112rpx; }
.profile-nickname-input { min-height: 80rpx; padding: 0 20rpx; border: 1rpx solid #ddd; border-radius: 16rpx; background: #fff; }
.profile-edit-actions { display: flex; gap: 20rpx; margin-top: 24rpx; }
.profile-edit-actions button { flex: 1; }
.profile-auth-error { display: block; margin-top: 12rpx; color: #b42339; font-size: 24rpx; line-height: 1.5; }
.person>view:nth-child(2) { flex: 1; min-width: 0; }
.person text { display: block; }
.person__name { font-size: 36rpx; font-weight: 800; }
.person__name--login { color: var(--color-primary-ink); }
.person__identity { display: flex; align-items: center; gap: 12rpx; }
.coser-status { width: 40rpx; height: 40rpx; box-sizing: border-box; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--color-primary); color: #fff; box-shadow: 0 4rpx 10rpx rgba(109,40,217,.22); }
.coser-status text { color: #fff; font-size: 24rpx; font-weight: 800; line-height: 1; }
.role-switch { margin-top: 26rpx; padding: 6rpx; display: flex; gap: 6rpx; border: 2rpx solid #e3dfee; border-radius: 24rpx; background: rgba(255,255,255,.78); }
.role-switch button { flex: 1; min-width: 0; min-height: 88rpx; margin: 0; padding: 0; display: flex; align-items: center; justify-content: center; border: 2rpx solid transparent; border-radius: 18rpx; background: transparent; color: var(--color-muted); font-size: 26rpx; line-height: 1; font-weight: 600; transition: background-color .18s ease, color .18s ease; }
.role-switch .active { border-color: #ddd6fe; background: #fff; color: var(--color-primary-ink); font-weight: 800; box-shadow: 0 6rpx 18rpx rgba(55,42,77,.09); }
.profile-page { padding-top: 4rpx; }
.commission .section__head button { min-height: 88rpx; margin: 0; padding: 0; border: 0; background: transparent; color: var(--color-primary); font-size: 24rpx; }
.status-grid { display: grid; grid-template-columns: repeat(4,1fr); gap: 8rpx; }
.status-grid button { position: relative; min-height: 132rpx; margin: 0; padding: 12rpx 4rpx; border: 0; background: transparent; color: var(--color-muted); font-size: 22rpx; display: flex; flex-direction: column; align-items: center; gap: 10rpx; }
.status-icon { width: 42rpx; height: 42rpx; box-sizing: border-box; border: 4rpx solid var(--color-primary); border-radius: 12rpx; }
.status-icon--clock { border-radius: 50%; }
.status-icon--card { height: 30rpx; margin-top: 6rpx; border-radius: 5rpx; }
.status-icon--calendar::before { content: ''; display: block; margin-top: 8rpx; border-top: 4rpx solid var(--color-primary); }
.status-icon--heart { border: 0; transform: rotate(45deg) scale(.7); background: var(--color-cta); border-radius: 8rpx 20rpx 8rpx 20rpx; }
.count { position: absolute; right: 12rpx; top: 0; width: 34rpx; height: 34rpx; border-radius: 50%; background: var(--color-cta); color: #fff; line-height: 34rpx; }
.entry { width: 100%; min-height: 124rpx; margin: 24rpx 0 0; padding: 20rpx 26rpx; display: flex; align-items: center; gap: 20rpx; border: 1rpx solid var(--color-border); border-radius: 28rpx; background: #fff; color: var(--color-text); text-align: left; box-shadow: 0 8rpx 24rpx rgba(55,42,77,.05); }
.entry__mark { width: 76rpx; height: 76rpx; flex: none; display: flex; align-items: center; justify-content: center; border-radius: 24rpx; background: var(--color-primary-soft); color: var(--color-primary); font-weight: 800; }
.entry__mark--pink { background: var(--color-cta-soft); color: var(--color-cta); }
.entry>view:nth-child(2) { flex: 1; min-width: 0; }
.entry>view:nth-child(2) text { display: block; }
.entry>view:nth-child(2) text:first-child { font-weight: 750; }
.entry>view:nth-child(2) text:last-child { margin-top: 4rpx; color: var(--color-muted); font-size: 23rpx; }
.menu button { width: 100%; min-height: 94rpx; margin: 0; padding: 0; display: flex; align-items: center; justify-content: space-between; border: 0; border-bottom: 1rpx solid var(--color-border); background: transparent; color: var(--color-text); font-size: 27rpx; }
.menu button:last-child { border-bottom: 0; }
.menu button text:last-child { color: var(--color-muted); font-size: 23rpx; }
.section-action { min-height: 72rpx; margin: 0; padding: 0 4rpx; border: 0; background: transparent; color: var(--color-primary); font-size: 25rpx; font-weight: 700; }
.coser-orders .section__head { margin-bottom: 8rpx; }
.coser-order-states { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); }
.coser-order-states button { position: relative; min-width: 0; min-height: 112rpx; margin: 0; padding: 12rpx 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4rpx; border: 0; background: transparent; color: var(--color-muted); font-size: 23rpx; }
.coser-order-states button + button::before { content: ''; position: absolute; left: 0; top: 22rpx; bottom: 22rpx; width: 1rpx; background: var(--color-border); }
.coser-order-states__count { color: var(--color-text); font-size: 34rpx; font-weight: 800; line-height: 1.15; }
.coser-order-states__count--alert { color: var(--color-cta); }
.coser-schedule__month { display: block; margin-bottom: 16rpx; color: var(--color-muted); font-size: 25rpx; font-weight: 700; }
.coser-schedule__week, .coser-schedule__grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 6rpx; text-align: center; }
.coser-schedule__week { margin-bottom: 8rpx; color: var(--color-muted); font-size: 22rpx; }
.coser-schedule__day { min-height: 64rpx; padding: 4rpx 0; box-sizing: border-box; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3rpx; border-radius: 12rpx; background: #f6f4f8; color: var(--color-muted); font-size: 24rpx; line-height: 1.1; }
.coser-schedule__day--blank { background: transparent; }
.coser-schedule__day--open { background: var(--color-success-soft); color: #126a49; font-weight: 700; }
.coser-schedule__day--occupied { background: #fff4e5; color: #b54708; font-weight: 700; }
.coser-schedule__day--today { box-shadow: inset 0 0 0 2rpx var(--color-primary); }
.coser-schedule__dot { width: 6rpx; height: 6rpx; border-radius: 50%; background: #a8a0ae; }
.coser-schedule__day--open .coser-schedule__dot { background: var(--color-success); }
.coser-schedule__day--occupied .coser-schedule__dot { background: #f79009; }
.coser-schedule__legend { display: flex; justify-content: flex-end; gap: 20rpx; margin-top: 14rpx; color: var(--color-muted); font-size: 22rpx; }
.coser-schedule__legend-item { display: flex; align-items: center; gap: 6rpx; }
.coser-schedule__legend-open, .coser-schedule__legend-closed, .coser-schedule__legend-occupied { width: 12rpx; height: 12rpx; border-radius: 50%; background: var(--color-success); }
.coser-schedule__legend-closed { background: #a8a0ae; }
.coser-schedule__legend-occupied { background: #f79009; }
.coser-settlement .section__head { margin-bottom: 4rpx; }
.coser-settlement .line { min-height: 76rpx; font-size: 26rpx; }
.coser-performance__grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 16rpx; margin: 16rpx 0; }
.coser-performance__grid>view { min-height: 116rpx; padding: 18rpx; box-sizing: border-box; border-radius: 18rpx; background: var(--color-primary-soft); }
.coser-performance__grid text { display: block; }
.coser-performance__grid text:first-child { color: var(--color-primary-ink); font-size: 40rpx; font-weight: 800; }
.coser-performance__grid text:last-child { margin-top: 5rpx; color: var(--color-muted); font-size: 22rpx; }

</style>
