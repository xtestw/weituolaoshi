const { get, set } = require('../../utils/store')
const { CITIES } = require('../../utils/cities')
const { guardPublishing } = require('../../utils/eligibility')
const { request } = require('../../services/api')

Page({
  data: { verified: false, reviewStatus: 'unverified', reviewReason: '', posts: [], coserCity: '', cities: CITIES, cityPickerOpen: false, openEditorAfterCity: false },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 1 })
    const review = get('coserVerification', { status: 'unverified', reason: '' })
    this.setData({ verified: review.status === 'verified', reviewStatus: review.status, reviewReason: review.reason || '', coserCity: get('coserCity', '') })
    if (wx.getStorageSync('account_token')) request('/v1/identity/me').then(identity => {
      const verification = { verified: identity.status === 'verified', adult: identity.adult === true, gender: identity.gender, genderEligible: identity.genderEligible === true }
      set('verification', verification)
      const verified = verification.verified && identity.coserStatus === 'verified'
      set('coserVerification', { status: verified ? 'verified' : identity.status || 'unverified', reason: identity.reason || '' })
      this.setData({ verified, reviewStatus: verified ? 'verified' : identity.status || 'unverified' })
    }).catch(() => {})
    if (wx.getStorageSync('account_token')) request('/v1/services/mine').then(rows => this.setData({ posts: rows.map(item => ({ ...item, price: Number(item.pricingUnit === 'day' ? item.pricePerDay : item.pricePerHour) / 100, dayPrice: Number(item.pricePerDay || 0) / 100, statusText: item.status === 'draft' ? '草稿' : '已发布' })) })).catch(() => this.setData({ posts: [] }))
  },
  async newPost() {
    if (wx.getStorageSync('account_token')) {
      try {
        const identity = await request('/v1/identity/me')
        set('verification', { verified: identity.status === 'verified', adult: identity.adult === true, gender: identity.gender, genderEligible: identity.genderEligible === true })
        const verified = identity.status === 'verified' && identity.coserStatus === 'verified'
        set('coserVerification', { status: verified ? 'verified' : identity.status || 'unverified' })
        this.setData({ verified })
      } catch (error) { return wx.showToast({ title: error.message || '认证状态获取失败', icon: 'none' }) }
    }
    if (get('verification', null) && !guardPublishing()) return
    if (!this.data.verified) return wx.navigateTo({ url: '/pages/verify/index?returnTo=publish&mode=coser' })
    if (!this.data.coserCity) return this.setData({ cityPickerOpen: true, openEditorAfterCity: true })
    wx.navigateTo({ url: '/pages/post-editor/index' })
  },
  chooseCity(event) {
    const city = event.currentTarget.dataset.city
    if (!CITIES.includes(city)) return
    set('coserCity', city)
    const openEditor = this.data.openEditorAfterCity
    this.setData({ coserCity: city, cityPickerOpen: false, openEditorAfterCity: false })
    if (openEditor) wx.navigateTo({ url: '/pages/post-editor/index' })
  },
  closeCityPicker() { this.setData({ cityPickerOpen: false, openEditorAfterCity: false }) },
  stopTap() {},
  refreshCity() { this.setData({ cityPickerOpen: true, openEditorAfterCity: false }) },
  viewHomepage() { const post = this.data.posts.find(item => item.status === 'published'); if (post) wx.navigateTo({ url: `/pages/coser/index?id=${post.id}` }); else wx.showToast({ title: '请先发布服务帖', icon: 'none' }) },
  edit(event) {
    const post = this.data.posts[event.currentTarget.dataset.index]
    if (post.locked) return wx.showModal({ title: '暂时不能修改', content: '已有进行中委托的帖子不能修改价格、时长与服务内容。订单结束后可继续编辑。', showCancel: false })
    set('draftPost', post)
    wx.navigateTo({ url: `/pages/post-editor/index?id=${post.id}` })
  },
  remove(event) {
    const post = this.data.posts[event.currentTarget.dataset.index]
    if (post.locked) return wx.showToast({ title: '进行中帖子不能删除', icon: 'none' })
    wx.showModal({ title: '删除这篇帖子？', content: '删除后无法恢复，已生成的历史订单不受影响。', confirmColor: '#c9384f', success: async ({ confirm }) => {
      if (!confirm) return
      try { await request(`/v1/services/${post.id}`, { method: 'DELETE' }); this.setData({ posts: this.data.posts.filter(item => item.id !== post.id) }) } catch(error) { wx.showToast({ title: error.message, icon: 'none' }) }
    } })
  },
  
})
