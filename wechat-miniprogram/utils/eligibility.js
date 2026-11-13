const { get } = require('./store')
function bookingEligibility() {
  const identity = get('verification', null)
  if (!identity || !identity.verified) return { allowed: false, reason: 'verify', title: '完成身份核验后下单', content: '浏览无需认证。下单前需核验身份，确认年满18周岁且符合平台服务范围。' }
  if (!identity.adult) return { allowed: false, reason: 'minor', title: '暂不支持下单', content: '未满18周岁可以继续浏览内容，暂不能下单或发布服务。' }
  if (identity.gender !== 'F') return { allowed: false, reason: 'male', title: '暂不支持下单', content: '平台目前仅向女性提供委托下单服务。你仍可继续浏览内容。' }
  return { allowed: true }
}
function guardBooking() {
  const result = bookingEligibility()
  if (result.allowed) return true
  wx.showModal({ title: result.title, content: result.content, confirmText: result.reason === 'verify' ? '去认证' : '我知道了', showCancel: result.reason === 'verify', success: ({ confirm }) => {
    if (confirm && result.reason === 'verify') wx.navigateTo({ url: '/pages/verify/index' })
  } })
  return false
}
module.exports = { bookingEligibility, guardBooking }
