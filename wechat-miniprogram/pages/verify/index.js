const { set } = require('../../utils/store')

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
  submit() {
    const name = this.data.name.trim()
    const id = this.data.idNumber.trim().toUpperCase()
    const errors = {}
    if (name.length < 2) errors.name = '请输入身份证上的真实姓名'
    if (!/^\d{17}[\dX]$/.test(id)) errors.idNumber = '请输入有效的18位身份证号码'
    if (!this.data.agreed) errors.agreed = '请阅读并同意身份核验授权'
    this.setData({ errors })
    if (Object.keys(errors).length) return
    const result = parseId(id)
    if (!result.adult) return wx.showModal({ title: '无法继续', content: '平台不允许未满18周岁的用户下单或发布服务。', showCancel: false })
    this.setData({ submitting: true })
    setTimeout(() => {
      set('verification', { verified: true, adult: true, gender: result.gender, maskedId: `${id.slice(0, 3)}***********${id.slice(-3)}` })
      if (this.data.mode === 'coser') set('coserVerification', { status: 'pending', reason: '', submittedAt: Date.now() })
      this.setData({ submitting: false })
      wx.showToast({ title: this.data.mode === 'coser' ? '已提交审核' : '认证完成' })
      setTimeout(() => wx.navigateBack(), 500)
    }, 650)
  }
})
