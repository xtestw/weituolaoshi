const { seedState } = require('./utils/store')

App({
  onLaunch() {
    seedState()
    const isDevtools = wx.getSystemInfoSync().platform === 'devtools'
    this.globalData.localDevMode = isDevtools
    if (isDevtools) this.globalData.apiBaseUrl = 'http://127.0.0.1:8080'
  },
  globalData: {
    apiBaseUrl: '',
    useDemoAccount: false,
    currentCity: '上海',
    role: 'customer'
  }
})
