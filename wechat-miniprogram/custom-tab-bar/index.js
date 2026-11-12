Component({
  data: {
    selected: 0,
    hidden: false,
    tabs: [
      { pagePath: '/pages/home/index', text: '首页', type: 'home' },
      { pagePath: '/pages/publish/index', text: 'Coser发布', type: 'plus' },
      { pagePath: '/pages/messages/index', text: '消息', type: 'message' },
      { pagePath: '/pages/profile/index', text: '我的', type: 'user' }
    ]
  },
  methods: {
    switchTab(event) {
      const { path } = event.currentTarget.dataset
      wx.switchTab({ url: path })
    }
  }
})
