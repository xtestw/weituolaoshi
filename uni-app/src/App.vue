<script>
import { seedState } from './utils/store'
import config from './config'

export default {
  globalData: {
    apiBaseUrl: config.apiBaseUrl,
    useDemoAccount: false,
    currentCity: config.currentCity,
    role: 'customer',
    localDevMode: false
  },
  onLaunch() {
    seedState()
    const isDevtools = uni.getSystemInfoSync().platform === 'devtools'
    let envVersion = ''
    try { envVersion = uni.getAccountInfoSync().miniProgram.envVersion } catch (_) {}
    const localDevMode = process.env.NODE_ENV === 'development' && isDevtools && envVersion === 'develop'
    this.globalData.localDevMode = localDevMode
    this.globalData.apiBaseUrl = localDevMode ? config.devtoolsApiBaseUrl : config.apiBaseUrl
    this.globalData.useDemoAccount = localDevMode && config.useDemoAccount
  }
}
</script>
<style>
@import './app.css';
</style>
