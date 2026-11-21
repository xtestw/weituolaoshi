const { set } = require('../../utils/store')
const { startFaceIdentity, completeFaceIdentity, currentUser } = require('../../services/api')

function parseId(id) {
  const birth = id.slice(6, 14)
  const year = Number(birth.slice(0, 4))
  const month = Number(birth.slice(4, 6))
  const day = Number(birth.slice(6, 8))
  const now = new Date()
  let age = now.getFullYear() - year
  if (now.getMonth() + 1 < month || (now.getMonth() + 1 === month && now.getDate() < day)) age -= 1
  return { adult: age >= 18, age, gender: Number(id[16]) % 2 === 1 ? 'M' : 'F' }
}

Page({
  data: { name: '', idNumber: '', agreed: false, returnTo: '', mode: 'customer', errors: {}, submitting: false },
  onLoad(options) {
    const mode = options.mode || 'customer'
    this.setData({ returnTo: options.returnTo || '', mode })
    if (mode === 'coser') wx.setNavigationBarTitle({ title: 'Coser真人认证' })
  },
  onField(event) { this.setData({ [event.currentTarget.dataset.field]: event.detail.value }) },
  onAgree(event) { this.setData({ agreed: event.detail.value.length > 0 }) },
  async submit() {
    if (this.data.submitting) return
    const name = this.data.name.trim()
    const id = this.data.idNumber.trim().toUpperCase()
    const errors = {}
    if (name.length < 2) errors.name = '请输入身份证上的真实姓名'
    if (!/^\d{17}[\dX]$/.test(id)) errors.idNumber = '请输入有效的18位身份证号码'
    if (!this.data.agreed) errors.agreed = '请阅读并同意身份核验授权'
    this.setData({ errors })
    if (Object.keys(errors).length) return
    const result = parseId(id)
    if (!result.adult) return this.setData({ errors: { submit: '未满18周岁可继续浏览，暂不能下单或发布服务。' } })
    if (this.data.mode === 'coser' && result.gender !== 'F')
      return this.setData({ errors: { submit: '全女平台仅支持女性申请Coser认证。' } })
    if (!wx.getStorageSync('account_token'))
      return this.setData({ errors: { submit: '请先在“我的”页面完成微信登录。' } })
    if (typeof wx.startVerify !== 'function')
      return this.setData({ errors: { submit: '腾讯云人脸核身 SDK 尚未安装，请联系平台。' } })
    this.setData({ submitting: true })
    try {
      const { bizToken } = await startFaceIdentity({ name, idNumber: id, mode: this.data.mode, agreed: true })
      this.setData({ idNumber: '' })
      wx.startVerify({
        data: { token: bizToken },
        success: () => setTimeout(async () => {
          try {
            const identity = await completeFaceIdentity(bizToken)
            set('verification', { verified: identity.status === 'verified', adult: identity.adult === true, gender: identity.gender })
            if (this.data.mode === 'coser') set('coserVerification', { status: identity.status, reason: '', submittedAt: Date.now() })
            await currentUser()
            wx.showToast({ title: '人脸核身完成', icon: 'success' })
            setTimeout(() => wx.navigateBack(), 500)
          } catch (error) { this.setData({ errors: { submit: error.message || '核身结果未通过' } }) }
          finally { this.setData({ submitting: false }) }
        }, 500),
        fail: error => { this.setData({ submitting: false, errors: { submit: error.ErrorMsg || '人脸核身未完成' } }) }
      })
    } catch (error) {
      this.setData({ submitting: false, errors: { submit: error.message || '核身服务暂不可用' } })
    }
  }
})
