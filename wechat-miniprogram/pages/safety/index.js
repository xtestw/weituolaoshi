const { get, set } = require('../../utils/store')

Page({
  data: { verified: false, name: '', phone: '', relation: '家人', relationOptions: ['家人', '朋友', '其他'], error: '', saved: false },
  onShow() {
    const contact = get('emergencyContact', null)
    this.setData({ verified: !!get('verification', null), name: contact ? contact.name : '', phone: contact ? contact.phone : '', relation: contact ? contact.relation : '家人', saved: !!contact, error: '' })
  },
  editName(event) { this.setData({ name: event.detail.value, error: '' }) },
  editPhone(event) { this.setData({ phone: event.detail.value, error: '' }) },
  chooseRelation(event) { this.setData({ relation: this.data.relationOptions[Number(event.detail.value)] }) },
  saveContact() {
    const name = this.data.name.trim()
    const phone = this.data.phone.trim()
    if (!name) return this.setData({ error: '请填写紧急联系人姓名' })
    if (!/^1[3-9]\d{9}$/.test(phone)) return this.setData({ error: '请填写有效的中国大陆手机号' })
    set('emergencyContact', { name, phone, relation: this.data.relation })
    this.setData({ name, phone, saved: true, error: '' })
    wx.showToast({ title: '已保存紧急联系人', icon: 'success' })
  },
  openVerification() { wx.navigateTo({ url: '/pages/verify/index' }) }
})
