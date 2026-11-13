const { get } = require('./store')

function callEmergencyContact() {
  const contact = get('emergencyContact', null)
  if (!contact || !contact.phone) return wx.showModal({ title: '尚未设置紧急联系人', content: '请先到「我的－安全中心」设置紧急联系人。', showCancel: false })
  wx.showModal({ title: '联系紧急联系人', content: `确认呼叫 ${contact.name}（${contact.relation}）吗？确认后会调起系统拨号。`, confirmText: '确认呼叫', confirmColor: '#c9384f', success: ({ confirm }) => {
    if (confirm) wx.makePhoneCall({ phoneNumber: contact.phone, fail: () => wx.showToast({ title: '未能调起拨号，请手动联系', icon: 'none' }) })
  } })
}

module.exports = { callEmergencyContact }
