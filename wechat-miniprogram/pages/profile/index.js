const { get, set } = require('../../utils/store')
const { request, wechatLogin, currentUser, logout } = require('../../services/api')
Page({
  data: { role: 'customer', verified: false, genderLabel: '', coserVerified: false, favorites: 0, emergencyContactSet: false, bPendingCount: 1, bServiceCount: 1, averageScore: '—', scoredOrders: 0, coserIncompleteCount: 0, schedulePreview: [], loggedIn: false, loginLoading: false, logoutLoading: false, accountName: '微信登录 / 注册', accountInitial: '我', accountAvatar: '', authError: '' },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 3, hidden: false })
    const cachedUser = wx.getStorageSync('account_user')
    const coserVerified = !!wx.getStorageSync('account_token') && cachedUser && cachedUser.role === 'coser'
    const bOrderAccepted = get('bOrderAccepted', false)
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
    const role = coserVerified && get('role', 'customer') === 'coser' ? 'coser' : 'customer'
    if (!coserVerified) set('role', 'customer')
    this.setData({ role, verified: false, eligible: false, coserVerified, favorites: get('favorites', []).length, emergencyContactSet: !!get('emergencyContact', null), bPendingCount: bOrderAccepted ? 0 : 1, bServiceCount: bOrderAccepted ? 2 : 1, averageScore, scoredOrders: reviews.length, coserIncompleteCount, schedulePreview, scheduleMonth: `${year}年${month}月` })
    this.showAccount(wx.getStorageSync('account_token') ? wx.getStorageSync('account_user') : null)
    if (wx.getStorageSync('account_token')) currentUser().then(user => {
      this.showAccount(user)
      this.syncIdentity()
      if (user.role === 'coser') this.syncSchedule()
    }).catch(error => {
      if (error.statusCode === 401) this.showAccount(null)
      else this.setData({ authError: '账号暂时无法同步，请检查网络后重试' })
    })
  },
  showAccount(user) {
    const name = user && user.nickname ? user.nickname : '微信用户'
    const avatar = user && user.avatarUrl ? user.avatarUrl : ''
    const coserVerified = !!user && user.role === 'coser'
    const role = coserVerified && get('role', 'customer') === 'coser' ? 'coser' : 'customer'
    this.setData({ loggedIn: !!user, coserVerified, role, accountName: user ? name : '微信登录 / 注册', accountInitial: user ? name.slice(0, 1) : '我', accountAvatar: avatar && avatar.startsWith('/') ? `${getApp().globalData.apiBaseUrl}${avatar}` : avatar, authError: '' })
  },
  syncIdentity() {
    return request('/v1/identity/me').then(identity => {
      const verified = identity.status === 'verified'
      this.setData({ verified, eligible: verified && identity.adult === true && identity.gender === 'F' })
    }).catch(error => {
      if (error.statusCode !== 401) this.setData({ authError: '实名状态暂时无法同步，请稍后重试' })
    })
  },
  syncSchedule() {
    const today = new Date()
    const month = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`
    return request(`/v1/cosers/me/availability?month=${month}`).then(result => {
      const open = new Set(result.days.filter(day => day.open).map(day => day.date))
      this.setData({ schedulePreview: this.data.schedulePreview.map(day => day.blank ? day : { ...day, open: open.has(day.key) }) })
    }).catch(() => this.setData({ authError: '档期暂时无法同步，请稍后重试' }))
  },
  async loginAccount() {
    if (this.data.loginLoading || this.data.loggedIn) return
    this.setData({ loginLoading: true, authError: '' })
    try {
      const user = await wechatLogin()
      this.showAccount(user)
      await this.syncIdentity()
      if (user.role === 'coser') await this.syncSchedule()
    }
    catch (error) { this.setData({ authError: error.message === 'API_NOT_CONFIGURED' ? '服务尚未配置，请联系平台' : (error.message || '登录失败，请重试') }) }
    finally { this.setData({ loginLoading: false }) }
  },
  exitAccount() {
    if (this.data.logoutLoading) return
    wx.showModal({ title: '退出登录', content: '退出后需重新使用微信登录。', success: async ({ confirm }) => {
      if (!confirm) return
      this.setData({ logoutLoading: true, authError: '' })
      try {
        await logout()
        set('role', 'customer')
        this.showAccount(null)
        this.setData({ role: 'customer', coserVerified: false, verified: false, eligible: false })
        wx.showToast({ title: '已退出登录', icon: 'success' })
      }
      catch (error) { this.setData({ authError: error.message || '退出失败，请重试' }) }
      finally { this.setData({ logoutLoading: false }) }
    } })
  },
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
