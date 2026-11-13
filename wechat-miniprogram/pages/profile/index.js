const { request, wechatLogin, uploadAvatar } = require('../../services/api')
const { get, set } = require('../../utils/store')
Page({
  data: { user: null, avatarSrc: '', editingName: false, nicknameDraft: '', accountBusy: false, role: 'customer', verified: false, genderLabel: '', coserVerified: false, favorites: 0, emergencyContactSet: false, bPendingCount: 1, bServiceCount: 1, schedulePreview: [] },
  onShow() {
    if (wx.getStorageSync('account_token')) request('/v1/identity/me').then(result => {
      if (result.mode === 'coser') set('coserVerification', { status: result.status, reason: result.reason || '' })
      if (result.status === 'verified') set('verification', { adult: result.adult, gender: result.gender, maskedId: result.maskedId })
      if (result.status !== 'verified') set('verification', null)
      this.setData({ verified: result.status === 'verified', coserVerified: result.mode === 'coser' && result.status === 'verified', genderLabel: result.gender === 'F' ? '女' : '男' })
    }).catch(() => {})
    this.loadAccount()
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 3, hidden: false })
    const loggedIn = !!wx.getStorageSync('account_token')
    const coserReview = loggedIn ? get('coserVerification', { status: 'unverified' }) : { status: 'unverified' }
    const verification = loggedIn ? get('verification', null) : null
    const coserVerified = coserReview.status === 'verified'
    const bOrderAccepted = get('bOrderAccepted', false)
    const availability = get('coserAvailability', {})
    const today = new Date()
    const year = today.getFullYear()
    const month = today.getMonth() + 1
    const leading = (new Date(year, month - 1, 1).getDay() + 6) % 7
    const schedulePreview = Array.from({ length: leading }, (_, index) => ({ key: `blank-${index}`, blank: true }))
    for (let date = 1; date <= new Date(year, month, 0).getDate(); date++) {
      const key = `${year}-${String(month).padStart(2, '0')}-${String(date).padStart(2, '0')}`
      const value = availability[key]
      schedulePreview.push({ key, date, open: value === true || (Array.isArray(value) && value.length > 0), today: date === today.getDate() })
    }
    const role = coserVerified && get('role', 'customer') === 'coser' ? 'coser' : 'customer'
    if (!coserVerified) set('role', 'customer')
    this.setData({ role, verified: !!verification, genderLabel: verification ? (verification.gender === 'F' ? '女' : '男') : '', coserVerified, favorites: get('favorites', []).length, emergencyContactSet: !!get('emergencyContact', null), bPendingCount: bOrderAccepted ? 0 : 1, bServiceCount: bOrderAccepted ? 2 : 1, schedulePreview, scheduleMonth: `${year}年${month}月` })
  },
  loadAccount() {
    if (!wx.getStorageSync('account_token')) { this.setData({ user: null, avatarSrc: '' }); return }
    request('/v1/users/me').then(user => this.showUser(user)).catch(() => {
      wx.removeStorageSync('account_token'); set('verification', null); set('coserVerification', { status: 'unverified', reason: '' }); this.setData({ user: null, avatarSrc: '', verified: false, coserVerified: false })
    })
  },
  showUser(user) {
    this.setData({ user, avatarSrc: user.avatarUrl ? `${getApp().globalData.apiBaseUrl}${user.avatarUrl}` : '', nicknameDraft: user.nickname || '' })
  },
  async loginAccount() {
    if (this.data.accountBusy) return
    this.setData({ accountBusy: true })
    try { set('verification', null); set('coserVerification', { status: 'unverified', reason: '' }); this.showUser(await wechatLogin()); wx.showToast({ title: '登录成功' }) }
    catch (err) { wx.showToast({ title: err.message === 'API_NOT_CONFIGURED' ? '请先配置服务器地址' : (err.message || '登录失败'), icon: 'none' }) }
    finally { this.setData({ accountBusy: false }) }
  },
  editNickname() { if (this.data.user) this.setData({ editingName: true, nicknameDraft: this.data.user.nickname || '' }) },
  nicknameInput(event) { this.setData({ nicknameDraft: event.detail.value }) },
  cancelNickname() { this.setData({ editingName: false }) },
  async saveNickname() {
    if (this.data.accountBusy) return
    this.setData({ accountBusy: true })
    try { this.showUser(await request('/v1/users/me', { method: 'PATCH', data: { nickname: this.data.nicknameDraft } })); this.setData({ editingName: false }); wx.showToast({ title: '昵称已更新' }) }
    catch (err) { wx.showToast({ title: err.message || '保存失败', icon: 'none' }) }
    finally { this.setData({ accountBusy: false }) }
  },
  async chooseAvatar(event) {
    if (!this.data.user || this.data.accountBusy || !event.detail.avatarUrl) return
    this.setData({ accountBusy: true })
    try { this.showUser(await uploadAvatar(event.detail.avatarUrl)); wx.showToast({ title: '头像已更新' }) }
    catch (err) { wx.showToast({ title: err.message || '头像上传失败', icon: 'none' }) }
    finally { this.setData({ accountBusy: false }) }
  },
  logoutAccount() { request('/v1/auth/logout', { method: 'POST' }).catch(() => {}); wx.removeStorageSync('account_token'); set('verification', null); set('coserVerification', { status: 'unverified', reason: '' }); set('role', 'customer'); this.setData({ user: null, avatarSrc: '', editingName: false, verified: false, coserVerified: false, role: 'customer' }) },
  setRole(event) { if (!this.data.coserVerified) return; const role = event.currentTarget.dataset.role; set('role', role); this.setData({ role }) },
  openOrders() { wx.navigateTo({ url: '/pages/orders/index' }) },
  openVerify() { wx.navigateTo({ url: '/pages/verify/index' }) },
  openVerification() { this.data.verified ? this.openSafety() : this.openVerify() },
  openBOrders(event) {
    if (!this.data.coserVerified) return
    const filter = event.currentTarget.dataset.filter || 'all'
    wx.navigateTo({ url: `/pages/b-orders/index?filter=${filter}` })
  },
  openBCalendar() {
    if (this.data.coserVerified) wx.navigateTo({ url: '/pages/b-calendar/index' })
  },
  openFavorites() { wx.navigateTo({ url: '/pages/favorites/index' }) },
  openHelp() { wx.showModal({ title: '帮助与客服', content: '正式上线时接入微信客服与平台工单。', showCancel: false }) },
  openSafety() { wx.navigateTo({ url: '/pages/safety/index' }) }
})
