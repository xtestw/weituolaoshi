const { request, uploadAvatar } = require('../../services/api')
Page({
  data: { draftNickname: '', draftPhone: '', draftBio: '', draftAvatar: '', accountAvatar: '', accountInitial: '我', profileSaving: false, authError: '', firstLogin: false },
  onLoad(options) {
    if (!wx.getStorageSync('account_token')) return wx.navigateBack()
    const user = wx.getStorageSync('account_user') || {}
    const name = user.nickname || '微信用户'
    const avatar = user.avatarUrl || ''
    this.setData({ firstLogin: options.firstLogin === '1', draftNickname: name === '微信用户' ? '' : name, draftPhone: user.phone || '', draftBio: user.bio || '', accountInitial: name.slice(0, 1), accountAvatar: avatar.startsWith('/') ? `${getApp().globalData.apiBaseUrl}${avatar}` : avatar })
  },
  onNickname(event) { this.setData({ draftNickname: event.detail.value }) },
  onPhone(event) { this.setData({ draftPhone: event.detail.value }) },
  onBio(event) { this.setData({ draftBio: event.detail.value }) },
  onChooseAvatar(event) { if (event.detail.avatarUrl) this.setData({ draftAvatar: event.detail.avatarUrl }) },
  async saveProfile() {
    if (this.data.profileSaving) return
    const nickname = this.data.draftNickname.trim()
    const phone = this.data.draftPhone.trim()
    const bio = this.data.draftBio.trim()
    if (!nickname || nickname.length > 30) return this.setData({ authError: '昵称长度须为1至30字' })
    if (phone && !/^1[3-9]\d{9}$/.test(phone)) return this.setData({ authError: '请输入有效的11位手机号' })
    if (bio.length > 500) return this.setData({ authError: '个人简介不能超过500字' })
    if (this.data.firstLogin && !this.data.draftAvatar && !this.data.accountAvatar) return this.setData({ authError: '请先选择微信头像' })
    this.setData({ profileSaving: true, authError: '' })
    try {
      let user = await request('/v1/users/me', { method: 'PATCH', data: { nickname, phone, bio } })
      wx.setStorageSync('account_user', user)
      if (this.data.draftAvatar) {
        user = await uploadAvatar(this.data.draftAvatar)
        wx.setStorageSync('account_user', user)
      }
      wx.showToast({ title: '资料已保存', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 400)
    } catch (error) { this.setData({ authError: error.message || '保存失败，请重试' }) }
    finally { this.setData({ profileSaving: false }) }
  }
})
