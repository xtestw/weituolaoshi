const { get, set } = require('../../utils/store')
const { cityFromCoordinates } = require('../../utils/cities')
const { request } = require('../../services/api')

Page({
  data: { verified: false, reviewStatus: 'unverified', reviewReason: '', posts: [], coserCity: '' },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 1 })
    const review = wx.getStorageSync('account_token') ? get('coserVerification', { status: 'unverified', reason: '' }) : { status: 'unverified', reason: '' }
    this.setData({ verified: review.status === 'verified', reviewStatus: review.status, reviewReason: review.reason || '', posts: get('posts', []), coserCity: get('coserCity', '') })
    if (wx.getStorageSync('account_token')) request('/v1/identity/me').then(result => {
      if (result.mode !== 'coser') return
      set('coserVerification', { status: result.status, reason: result.reason || '' })
      this.setData({ verified: result.status === 'verified', reviewStatus: result.status, reviewReason: result.reason || '' })
    }).catch(() => {})
  },
  newPost() {
    if (!wx.getStorageSync('account_token')) return wx.showModal({ title: '请先登录', content: '请先在“我的”页面完成微信登录。', showCancel: false })
    if (this.data.reviewStatus === 'pending') return wx.showModal({ title: '认证审核中', content: '审核结果会通过平台消息通知，通过后即可发布帖子。', showCancel: false })
    if (!this.data.verified) return wx.navigateTo({ url: '/pages/verify/index?returnTo=publish&mode=coser' })
    if (!this.data.coserCity) return this.locateCity(() => wx.navigateTo({ url: '/pages/post-editor/index' }))
    wx.navigateTo({ url: '/pages/post-editor/index' })
  },
  locateCity(done) {
    wx.getLocation({ type: 'wgs84', success: ({ latitude, longitude }) => {
      const city = cityFromCoordinates(latitude, longitude)
      if (!city) return wx.showModal({ title: '暂未开放', content: '当前位置不在平台开放的13个城市内，暂不能发布服务。', showCancel: false })
      set('coserCity', city)
      this.setData({ coserCity: city })
      wx.showToast({ title: `已定位到${city}`, icon: 'none' })
      if (typeof done === 'function') done()
    }, fail: () => wx.showToast({ title: '请允许定位后重试', icon: 'none' }) })
  },
  refreshCity() { this.locateCity() },
  viewHomepage() { wx.navigateTo({ url: '/pages/coser/index?id=mine' }) },
  edit(event) {
    const post = this.data.posts[event.currentTarget.dataset.index]
    if (post.locked) return wx.showModal({ title: '暂时不能修改', content: '已有进行中委托的帖子不能修改价格、时长与服务内容。订单结束后可继续编辑。', showCancel: false })
    set('draftPost', post)
    wx.navigateTo({ url: `/pages/post-editor/index?id=${post.id}` })
  },
  remove(event) {
    const post = this.data.posts[event.currentTarget.dataset.index]
    if (post.locked) return wx.showToast({ title: '进行中帖子不能删除', icon: 'none' })
    wx.showModal({ title: '删除这篇帖子？', content: '删除后无法恢复，已生成的历史订单不受影响。', confirmColor: '#c9384f', success: ({ confirm }) => {
      if (!confirm) return
      const posts = this.data.posts.filter((item) => item.id !== post.id)
      set('posts', posts); this.setData({ posts })
    } })
  },
  
})
