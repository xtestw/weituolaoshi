const { seedState } = require('./utils/store')
const FaceVerify = require('./verify_mpsdk/main')

App({
  onLaunch() {
    seedState()
    FaceVerify.init()
    const isDevtools = wx.getSystemInfoSync().platform === 'devtools'
    this.globalData.localDevMode = isDevtools
    if (isDevtools) {
      this.globalData.apiBaseUrl = 'http://127.0.0.1:18080'
      this.globalData.useDemoAccount = true
    }
  },
  globalData: {
    apiBaseUrl: '',
    useDemoAccount: false,
    currentCity: '上海',
    role: 'customer'
  }
})
