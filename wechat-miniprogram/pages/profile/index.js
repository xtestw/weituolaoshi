const { get, set } = require('../../utils/store')
Page({
  data: { role: 'customer', verified: false, genderLabel: '', coserVerified: false, favorites: 0, emergencyContactSet: false, bPendingCount: 1, bServiceCount: 1, schedulePreview: [] },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 3, hidden: false })
    const coserReview = get('coserVerification', { status: 'unverified' })
    const verification = get('verification', null)
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
