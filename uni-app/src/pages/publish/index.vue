<template>
<view class="safe-top publish-head"><view class="publish-head__bar"><text>Coser发布</text></view></view>
<view class="page publish-page">
  <view v-if="verified" class="publish-summary"><view><text>创作中心</text><text>已认证 · 可发布多个服务帖子</text></view><button @tap="viewHomepage">预览主页</button></view>

  <view v-if="verified" class="coser-city" @tap="refreshCity">
    <text class="coser-city__label">服务城市</text>
    <text class="coser-city__value">{{ coserCity ? coserCity + ' · 更改' : '选择服务城市' }}</text>
    <view class="control-chevron" aria-hidden="true"></view>
  </view>
  <view v-if="cityPickerOpen" class="city-mask" @tap="closeCityPicker"><view class="city-sheet" @tap.stop="stopTap"><view class="city-head"><text>选择服务城市</text><button @tap="closeCityPicker">关闭</button></view><view class="city-grid"><button v-for="item in cities" :key="item" :data-city="item" @tap="chooseCity">{{ item }}</button></view></view></view>

  <view class="primary-actions"><button class="new-post" @tap="newPost"><view class="plus"></view><view><text>发布新帖</text><text>{{ verified ? '发布新的角色服务' : '先完成真人认证' }}</text></view><view class="control-chevron" aria-hidden="true"></view></button></view>

  <template v-if="verified">
    <view class="section posts"><view class="section__head"><text class="section__title">我的帖子 · {{ posts.length }}</text></view>
      <view v-if="!posts.length" class="empty"><text>还没有帖子，发布第一篇角色服务吧</text></view>
      <view v-for="(item, index) in posts" :key="item.id" class="post"><view class="post__cover"><image v-if="item.coverDisplay" class="post__cover-image" :src="item.coverDisplay" mode="aspectFill"/><text v-else>{{ item.coverMark || 'COS' }}</text></view><view class="post__copy"><text>{{ item.title }}</text><text>¥{{ item.price }}/小时 · 最低{{ item.minHours }}小时</text><text :class="item.locked ? 'locked' : ''">{{ item.locked ? '进行中 · 已锁定' : item.statusText }}</text></view><view class="post__tools"><button :data-index="index" @tap="edit">编辑</button><button :data-index="index" @tap="remove">删除</button></view></view>
    </view>
  </template>
</view>

</template>
<script>
import { get, set } from '../../utils/store'
import { CITIES } from '../../utils/cities'
import { guardBooking } from '../../utils/eligibility'
import { request } from '../../services/api'

export default {
data() { return { verified: false, reviewStatus: 'unverified', reviewReason: '', posts: [], coserCity: '', cities: CITIES, cityPickerOpen: false, openEditorAfterCity: false } },
onShow() {

    const review = get('coserVerification', { status: 'unverified', reason: '' })
    this.setData({ verified: review.status === 'verified', reviewStatus: review.status, reviewReason: review.reason || '', coserCity: get('coserCity', '') })
    if (uni.getStorageSync('account_token')) request('/v1/identity/me').then(identity => {
      const verification = { verified: identity.status === 'verified', adult: identity.adult === true, gender: identity.gender, genderEligible: identity.genderEligible === true }
      set('verification', verification)
      const verified = verification.verified && identity.coserStatus === 'verified'
      set('coserVerification', { status: verified ? 'verified' : identity.status || 'unverified', reason: identity.reason || '' })
      this.setData({ verified, reviewStatus: verified ? 'verified' : identity.status || 'unverified' })
    }).catch(() => {})
    if (uni.getStorageSync('account_token')) request('/v1/services/mine').then(rows => this.setData({ posts: rows.map(item => {
      const coverUrl = item.coverUrl || ''
      const coverDisplay = coverUrl ? (coverUrl.startsWith('/') ? `${getApp().globalData.apiBaseUrl}${coverUrl}` : coverUrl) : ''
      return { ...item, price: Number(item.pricePerHour) / 100, statusText: item.status === 'draft' ? '草稿' : '已发布', coverDisplay }
    }) })).catch(() => this.setData({ posts: [] }))
  },
methods: {
async newPost() {
    if (uni.getStorageSync('account_token')) {
      try {
        const identity = await request('/v1/identity/me')
        set('verification', { verified: identity.status === 'verified', adult: identity.adult === true, gender: identity.gender, genderEligible: identity.genderEligible === true })
        const verified = identity.status === 'verified' && identity.coserStatus === 'verified'
        set('coserVerification', { status: verified ? 'verified' : identity.status || 'unverified' })
        this.setData({ verified })
      } catch (error) { return uni.showToast({ title: error.message || '认证状态获取失败', icon: 'none' }) }
    }
    if (get('verification', null) && !guardBooking()) return
    if (!this.verified) return uni.navigateTo({ url: '/pages/verify/index?returnTo=publish&mode=coser' })
    if (!this.coserCity) return this.setData({ cityPickerOpen: true, openEditorAfterCity: true })
    uni.navigateTo({ url: '/pages/post-editor/index' })
  },
chooseCity(event) {
    const city = event.currentTarget.dataset.city
    if (!CITIES.includes(city)) return
    set('coserCity', city)
    const openEditor = this.openEditorAfterCity
    this.setData({ coserCity: city, cityPickerOpen: false, openEditorAfterCity: false })
    if (openEditor) uni.navigateTo({ url: '/pages/post-editor/index' })
  },
closeCityPicker() { this.setData({ cityPickerOpen: false, openEditorAfterCity: false }) },
stopTap() {},
refreshCity() { this.setData({ cityPickerOpen: true, openEditorAfterCity: false }) },
viewHomepage() { const post = this.posts.find(item => item.status === 'published'); if (post) uni.navigateTo({ url: `/pages/coser/index?id=${post.id}` }); else uni.showToast({ title: '请先发布服务帖', icon: 'none' }) },
edit(event) {
    const post = this.posts[event.currentTarget.dataset.index]
    if (post.locked) return uni.showModal({ title: '暂时不能修改', content: '已有进行中委托的帖子不能修改价格、时长与服务内容。订单结束后可继续编辑。', showCancel: false })
    set('draftPost', post)
    uni.navigateTo({ url: `/pages/post-editor/index?id=${post.id}` })
  },
remove(event) {
    const post = this.posts[event.currentTarget.dataset.index]
    if (post.locked) return uni.showToast({ title: '进行中帖子不能删除', icon: 'none' })
    uni.showModal({ title: '删除这篇帖子？', content: '删除后无法恢复，已生成的历史订单不受影响。', confirmColor: '#c9384f', success: async ({ confirm }) => {
      if (!confirm) return
      try { await request(`/v1/services/${post.id}`, { method: 'DELETE' }); this.setData({ posts: this.posts.filter((item) => item.id !== post.id) }) } catch(error) { uni.showToast({ title: error.message, icon: 'none' }) }
    } })
  },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.publish-head { position: sticky; z-index: 30; top: 0; background: rgba(255,255,255,.97); border-bottom: 1rpx solid var(--color-border); }
.publish-head__bar { position: relative; height: 88rpx; padding: 0 28rpx; display: flex; align-items: center; justify-content: space-between; }
.publish-head__bar>text { font-size: var(--type-page); line-height: 1.25; font-weight: var(--weight-page); }
.publish-page { padding-top: 24rpx; }
.publish-summary { min-height: 126rpx; padding: 26rpx 28rpx; box-sizing: border-box; display: flex; align-items: center; justify-content: space-between; gap: 18rpx; border-radius: 28rpx; background: linear-gradient(135deg,var(--color-primary-soft),var(--color-cta-soft)); }
.publish-summary>view { flex: 1; min-width: 0; }
.publish-summary text { display: block; }
.publish-summary text:first-child { font-size: var(--type-card); font-weight: var(--weight-card); }
.publish-summary text:last-child { margin-top: 5rpx; color: var(--color-muted); font-size: 22rpx; }
.publish-summary button { flex: none; min-height: 88rpx; margin: 0; padding: 0 20rpx; display: flex; align-items: center; justify-content: center; border: 1rpx solid #ddd6fe; border-radius: 999rpx; background: #fff; color: var(--color-primary); font-size: 23rpx; line-height: 1; font-weight: 700; }
.coser-city { width: 100%; min-height: 96rpx; margin-top: 16rpx; padding: 0 24rpx; box-sizing: border-box; display: flex; align-items: center; gap: 16rpx; border: 1rpx solid rgba(231,226,236,.9); border-radius: 28rpx; background: var(--color-surface); box-shadow: var(--shadow-card); color: var(--color-text); }
.coser-city__label { flex: none; font-size: 26rpx; font-weight: 650; line-height: 1.3; }
.coser-city__value { flex: 1; min-width: 0; text-align: right; color: var(--color-primary); font-size: 25rpx; font-weight: 650; line-height: 1.3; }
.coser-city .control-chevron { color: var(--color-primary); }
.city-mask { position: fixed; inset: 0; z-index: 100; display: flex; align-items: flex-end; background: rgba(0,0,0,.4); }
.city-sheet { width: 100%; max-height: 70vh; overflow-y: auto; padding: 28rpx; box-sizing: border-box; border-radius: 28rpx 28rpx 0 0; background: #fff; }
.city-head { display: flex; align-items: center; justify-content: space-between; font-size: 32rpx; font-weight: 700; }
.city-head button { margin: 0; background: transparent; color: var(--color-primary); font-size: 25rpx; }
.city-grid { display: grid; grid-template-columns: repeat(3,1fr); gap: 14rpx; margin-top: 26rpx; }
.city-grid button { margin: 0; background: var(--color-bg); font-size: 26rpx; }
.publish-hero { position: relative; min-height: 230rpx; padding: 34rpx; overflow: hidden; border-radius: 34rpx; background: linear-gradient(135deg,var(--color-primary),#a78bfa); color: #fff; }
.publish-hero::after { content: ''; position: absolute; right: -50rpx; top: -60rpx; width: 240rpx; height: 240rpx; border-radius: 50%; background: rgba(255,255,255,.13); box-shadow: -120rpx 220rpx 0 20rpx rgba(255,255,255,.08); }
.publish-hero text { position: relative; z-index: 1; display: block; }
.publish-hero__eyebrow { opacity: .82; font-size: 23rpx; }
.publish-hero__title { margin: 10rpx 0; font-size: 38rpx; font-weight: 800; }
.publish-hero text:last-child { max-width: 76%; opacity: .88; font-size: 24rpx; }
.auth-notice { margin-top: 22rpx; }
.primary-actions { margin-top: 16rpx; display: grid; gap: 18rpx; }
.primary-actions button { width: 100%; min-height: 130rpx; margin: 0; padding: 24rpx; display: flex; align-items: center; gap: 22rpx; border: 1rpx solid rgba(231,226,236,.9); border-radius: 28rpx; background: #fff; color: var(--color-text); text-align: left; box-shadow: var(--shadow-card); }
.primary-actions .control-chevron { flex: none; color: var(--color-muted); }
.primary-actions button>view:nth-child(2) { flex: 1; min-width: 0; }
.primary-actions button>view:nth-child(2) text { display: block; }
.primary-actions button>view:nth-child(2) text:first-child { font-size: var(--type-card); font-weight: var(--weight-card); }
.primary-actions button>view:nth-child(2) text:last-child { margin-top: 4rpx; color: var(--color-muted); font-size: 23rpx; }
.plus,.eye { position: relative; width: 76rpx; height: 76rpx; flex: none; border-radius: 24rpx; background: var(--color-cta-soft); }
.plus::before,.plus::after { content: ''; position: absolute; left: 20rpx; top: 35rpx; width: 36rpx; height: 6rpx; border-radius: 6rpx; background: var(--color-cta); }
.plus::after { transform: rotate(90deg); }
.eye { background: var(--color-primary-soft); }
.eye::before { content: ''; position: absolute; left: 14rpx; top: 22rpx; width: 46rpx; height: 30rpx; border: 4rpx solid var(--color-primary); border-radius: 50% 10%; transform: rotate(45deg); }
.eye::after { content: ''; position: absolute; left: 32rpx; top: 32rpx; width: 12rpx; height: 12rpx; border-radius: 50%; background: var(--color-primary); }
.post { padding: 22rpx 0; display: flex; gap: 18rpx; align-items: center; border-bottom: 1rpx solid var(--color-border); }
.post:last-child { border-bottom: 0; }
.post__cover { width: 110rpx; height: 110rpx; flex: none; padding: 10rpx; box-sizing: border-box; display: flex; align-items: flex-end; border-radius: 18rpx; background: linear-gradient(145deg,#ede9fe,#ddd6fe); color: rgba(36,29,45,.62); font-size: 28rpx; font-weight: 800; overflow: hidden; position: relative; }
.post__cover-image { position: absolute; inset: 0; width: 100%; height: 100%; }
.post__copy { flex: 1; min-width: 0; }
.post__copy text { display: block; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.post__copy text:first-child { font-size: var(--type-body); font-weight: var(--weight-card); }
.post__copy text:nth-child(2),.post__copy text:nth-child(3) { color: var(--color-muted); font-size: 22rpx; }
.post__copy .locked { color: var(--color-cta)!important; }
.post__tools button { min-height: 88rpx; margin: 0; padding: 0 12rpx; border: 0; background: transparent; color: var(--color-primary); font-size: 23rpx; }
.post__tools button:last-child { color: var(--color-danger); }

</style>
