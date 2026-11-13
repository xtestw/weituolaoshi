const { set } = require('../../utils/store')
const { request } = require('../../services/api')

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
    const name = this.data.name.trim()
    const id = this.data.idNumber.trim().toUpperCase()
    const errors = {}
    if (name.length < 2) errors.name = '请输入身份证上的真实姓名'
    if (!/^\d{17}[\dX]$/.test(id)) errors.idNumber = '请输入有效的18位身份证号码'
    if (!this.data.agreed) errors.agreed = '请阅读并同意身份核验授权'
    this.setData({ errors })
    if (Object.keys(errors).length) return
    if (!wx.getStorageSync('account_token')) return wx.showModal({ title: '请先登录', content: '实名认证需要先在“我的”页面完成微信登录。', showCancel: false })
    this.setData({ submitting: true })
    try {
      const result = await request('/v1/identity/verifications', { method: 'POST', data: { name, idNumber: id, agreed: true, mode: this.data.mode } })
      if (this.data.mode === 'coser') { set('coserVerification', { status: result.status, reason: '' }); set('verification', null) }
      else set('verification', { verified: result.status === 'verified', adult: result.adult, gender: result.gender, maskedId: result.maskedId })
      this.setData({ idNumber: '', name: '' })
      wx.showToast({ title: result.status === 'pending' ? '已提交审核' : '认证完成' })
      setTimeout(() => wx.navigateBack(), 500)
    } catch (err) { wx.showModal({ title: '认证未完成', content: err.message || '请稍后重试', showCancel: false }) }
    finally { this.setData({ submitting: false }) }
  }
})
