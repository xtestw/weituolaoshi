const { seedState } = require('./utils/store')

App({
  onLaunch() {
    seedState()
  },
  globalData: {
    apiBaseUrl: '',
    currentCity: '上海',
    role: 'customer'
  }
})
