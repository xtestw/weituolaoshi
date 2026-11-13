const app = () => getApp()

function request(path, options = {}) {
  const base = app().globalData.apiBaseUrl
  if (!base) return Promise.reject(new Error('API_NOT_CONFIGURED'))
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${base}${path}`,
      method: options.method || 'GET',
      data: options.data,
      header: { 'content-type': 'application/json', ...(wx.getStorageSync('account_token') ? { Authorization: `Bearer ${wx.getStorageSync('account_token')}` } : {}), ...(options.header || {}) },
      success: ({ statusCode, data }) => {
        if (statusCode >= 200 && statusCode < 300) resolve(data)
        else reject(Object.assign(new Error(data.message || '请求失败'), { statusCode, data }))
      },
      fail: reject
    })
  })
}

function createPayment(orderId) {
  return request(`/v1/orders/${orderId}/payment`, { method: 'POST' })
    .then((payment) => new Promise((resolve, reject) => {
      wx.requestPayment({ ...payment, success: resolve, fail: reject })
    }))
}

function verifyIdentity(payload) {
  return request('/v1/identity/verifications', { method: 'POST', data: payload })
}

function wechatLogin() {
  return new Promise((resolve, reject) => wx.login({ success: resolve, fail: reject }))
    .then(({ code }) => request('/v1/auth/wechat-login', { method: 'POST', data: { code } }))
    .then(({ token, user }) => { wx.setStorageSync('account_token', token); return user })
}
function uploadAvatar(filePath) {
  const base = app().globalData.apiBaseUrl
  const token = wx.getStorageSync('account_token')
  if (!base || !token) return Promise.reject(new Error('请先登录'))
  return new Promise((resolve, reject) => wx.uploadFile({
    url: `${base}/v1/users/me/avatar`, filePath, name: 'file',
    header: { Authorization: `Bearer ${token}` },
    success: ({ statusCode, data }) => statusCode >= 200 && statusCode < 300 ? resolve(JSON.parse(data)) : reject(new Error('头像上传失败')),
    fail: reject
  }))
}
module.exports = { request, createPayment, verifyIdentity, wechatLogin, uploadAvatar }
