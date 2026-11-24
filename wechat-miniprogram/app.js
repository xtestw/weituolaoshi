const { seedState } = require('./utils/store')
const FaceVerify = require('./verify_mpsdk/main')
const config = require('./config')

App({
  onLaunch() {
    seedState()
    FaceVerify.init()
    const isDevtools = wx.getSystemInfoSync().platform === 'devtools'
    this.globalData.localDevMode = isDevtools
    this.globalData.apiBaseUrl = isDevtools ? config.devtoolsApiBaseUrl : config.apiBaseUrl
  },
  globalData: {
    apiBaseUrl: config.apiBaseUrl,
    useDemoAccount: config.useDemoAccount,
    currentCity: config.currentCity,
    role: 'customer'
  }
})
