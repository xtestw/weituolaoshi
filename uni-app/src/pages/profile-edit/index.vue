<template>
<view class="profile-edit-page">
  <view v-if="firstLogin" class="profile-setup-intro"><text class="profile-setup-title">设置微信头像和昵称</text><text class="profile-setup-hint">首次登录请先选择头像和昵称，完成后即可使用账号。</text></view>
  <view class="profile-card profile-avatar-row">
    <view><text class="profile-row-title">头像</text><text class="profile-row-hint">{{ firstLogin ? '点击右侧选择微信头像' : '点击右侧头像更换' }}</text></view>
    <button class="profile-avatar-picker" open-type="chooseAvatar" @chooseavatar="onChooseAvatar" aria-label="选择头像"><image v-if="draftAvatar || accountAvatar" :src="draftAvatar || accountAvatar" mode="aspectFill"/><text v-else>{{ accountInitial }}</text></button>
  </view>
  <view class="profile-card profile-fields">
    <view class="profile-field-row"><text class="profile-row-title">昵称</text><input type="nickname" :value="draftNickname" maxlength="30" @input="onNickname" :placeholder="firstLogin ? '点击选择微信昵称' : '填写昵称'"/></view>
    <view class="profile-field-row"><view><text class="profile-row-title">手机号</text><text class="profile-row-hint">未验证 · 选填</text></view><input type="number" :value="draftPhone" maxlength="11" @input="onPhone" placeholder="填写手机号"/></view>
    <view class="profile-bio-row"><text class="profile-row-title">个人简介</text><textarea :value="draftBio" maxlength="500" @input="onBio" placeholder="介绍一下自己（选填）"/></view>
  </view>
  <text v-if="authError" class="profile-edit-error">{{ authError }}</text>
  <view class="profile-save-area"><button class="profile-save-button" :loading="profileSaving" :disabled="profileSaving" @tap="saveProfile">保存资料</button></view>
</view>
</template>
<script>
import { request, uploadAvatar } from '../../services/api'
export default {
  data() { return { draftNickname: '', draftPhone: '', draftBio: '', draftAvatar: '', accountAvatar: '', accountInitial: '我', profileSaving: false, authError: '', firstLogin: false } },
  onLoad(options) {
    if (!uni.getStorageSync('account_token')) return uni.navigateBack()
    const user = uni.getStorageSync('account_user') || {}
    const name = user.nickname || '微信用户'
    const avatar = user.avatarUrl || ''
    this.firstLogin = options.firstLogin === '1' || !avatar || name === '微信用户'
    this.draftNickname = name === '微信用户' ? '' : name
    this.draftPhone = user.phone || ''
    this.draftBio = user.bio || ''
    this.accountInitial = name.slice(0, 1)
    this.accountAvatar = avatar.startsWith('/') ? `${getApp().globalData.apiBaseUrl}${avatar}` : avatar
  },
  methods: {
    onNickname(event) { this.draftNickname = event.detail.value },
    onPhone(event) { this.draftPhone = event.detail.value },
    onBio(event) { this.draftBio = event.detail.value },
    onChooseAvatar(event) { if (event.detail.avatarUrl) this.draftAvatar = event.detail.avatarUrl },
    async saveProfile() {
      if (this.profileSaving) return
      const nickname = this.draftNickname.trim()
      const phone = this.draftPhone.trim()
      const bio = this.draftBio.trim()
      if (!nickname || nickname.length > 30) return this.authError = '昵称长度须为1至30字'
      if (phone && !/^1[3-9]\d{9}$/.test(phone)) return this.authError = '请输入有效的11位手机号'
      if (bio.length > 500) return this.authError = '个人简介不能超过500字'
      if (this.firstLogin && !this.draftAvatar && !this.accountAvatar) return this.authError = '请先选择微信头像'
      this.profileSaving = true
      this.authError = ''
      try {
        let user = await request('/v1/users/me', { method: 'PATCH', data: { nickname, phone, bio } })
        uni.setStorageSync('account_user', user)
        if (this.draftAvatar) {
          user = await uploadAvatar(this.draftAvatar)
          uni.setStorageSync('account_user', user)
        }
        uni.showToast({ title: '资料已保存', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 400)
      } catch (error) { this.authError = error.message || '保存失败，请重试' }
      finally { this.profileSaving = false }
    }
  }
}
</script>
<style>
.profile-edit-page { min-height: 100vh; box-sizing: border-box; padding: 24rpx 28rpx calc(148rpx + env(safe-area-inset-bottom)); background: #f7f6fb; }
.profile-setup-intro { margin: 4rpx 4rpx 24rpx; }
.profile-setup-title { display: block; color: #25202d; font-size: 34rpx; font-weight: 700; }
.profile-setup-hint { display: block; margin-top: 10rpx; color: #8b8492; font-size: 24rpx; line-height: 1.5; }
.profile-card { margin-bottom: 20rpx; padding: 0 28rpx; border-radius: 24rpx; background: #fff; box-shadow: 0 4rpx 18rpx rgba(55,42,77,.04); }
.profile-avatar-row { min-height: 150rpx; display: flex; align-items: center; justify-content: space-between; gap: 20rpx; }
.profile-row-title { display: block; color: #25202d; font-size: 28rpx; font-weight: 650; line-height: 1.4; }
.profile-row-hint { display: block; margin-top: 5rpx; color: #8b8492; font-size: 22rpx; line-height: 1.3; }
.profile-avatar-picker { width: 96rpx; height: 96rpx; min-width: 96rpx; min-height: 96rpx; margin: 0; padding: 0; overflow: hidden; border-radius: 50%; background: #eee7ff; color: #4c21a2; font-size: 34rpx; font-weight: 750; line-height: 96rpx; text-align: center; }
.profile-avatar-picker image { width: 96rpx; height: 96rpx; border-radius: 50%; }
.profile-field-row { min-height: 112rpx; display: flex; align-items: center; justify-content: space-between; gap: 24rpx; border-bottom: 1rpx solid #eeeaf2; }
.profile-field-row input { width: 58%; height: 72rpx; min-height: 72rpx; padding: 0; color: #25202d; font-size: 27rpx; text-align: right; }
.profile-bio-row { padding: 25rpx 0 20rpx; }
.profile-bio-row textarea { width: 100%; height: 126rpx; min-height: 126rpx; box-sizing: border-box; margin-top: 16rpx; padding: 0; color: #25202d; font-size: 26rpx; line-height: 1.5; }
.profile-edit-error { display: block; margin: 4rpx 6rpx 18rpx; color: #b42339; font-size: 24rpx; }
.profile-save-area { position: fixed; left: 0; right: 0; bottom: 0; padding: 16rpx 28rpx calc(16rpx + env(safe-area-inset-bottom)); background: #fff; border-top: 1rpx solid #eeeaf2; }
.profile-save-button { width: 100%; height: 88rpx; margin: 0; padding: 0; border-radius: 20rpx; background: var(--color-primary); color: #fff; font-size: 28rpx; font-weight: 700; line-height: 88rpx; text-align: center; }
</style>
