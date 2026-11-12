const { get, set } = require('../../utils/store')
const { services } = require('../../utils/services')

Page({
  data: { items: [] },
  onShow() {
    const ids = get('favorites', [])
    this.setData({ items: ids.map((id) => services.find((item) => item.id === id)).filter(Boolean) })
  },
  openService(event) { wx.navigateTo({ url: `/pages/coser/index?id=${event.currentTarget.dataset.id}` }) },
  removeFavorite(event) {
    const id = event.currentTarget.dataset.id
    set('favorites', get('favorites', []).filter((item) => item !== id))
    this.onShow()
    wx.showToast({ title: '已取消收藏', icon: 'none' })
  },
  browse() { wx.switchTab({ url: '/pages/home/index' }) }
})
