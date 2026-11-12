Component({
  properties: { selected: { type: String, value: 'dashboard' } },
  data: {
    tabs: [
      { key: 'dashboard', mark: '台', label: '工作台', path: '/pages/b-dashboard/index' },
      { key: 'orders', mark: '单', label: '订单', path: '/pages/b-orders/index' },
      { key: 'calendar', mark: '历', label: '档期', path: '/pages/b-calendar/index' },
      { key: 'profile', mark: '我', label: '我的', path: '/pages/b-profile/index' }
    ]
  },
  methods: {
    navigate(event) {
      const item = this.data.tabs.find((tab) => tab.key === event.currentTarget.dataset.key)
      if (!item || item.key === this.data.selected) return
      wx.redirectTo({ url: item.path })
    }
  }
})
