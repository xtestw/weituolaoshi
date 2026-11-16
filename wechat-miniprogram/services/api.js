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
        else {
          if (statusCode === 401 && wx.getStorageSync('account_token')) clearAccount()
          reject(Object.assign(new Error((data && data.message) || '请求失败'), { statusCode, data }))
        }
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
  if (app().globalData.useDemoAccount) {
    return request('/v1/dev/login', { method: 'POST' }).then(({ token, user }) => {
      wx.setStorageSync('account_token', token)
      wx.setStorageSync('account_user', user)
      return user
    })
  }
  return new Promise((resolve, reject) => wx.login({ success: resolve, fail: reject }))
    .then(({ code }) => {
      if (!code) throw new Error('未获取到微信登录凭证，请重试')
      return request('/v1/auth/wechat-login', { method: 'POST', data: { code } })
    })
    .then(({ token, user }) => {
      if (!token || !user) throw new Error('登录响应不完整，请重试')
      wx.setStorageSync('account_token', token)
      wx.setStorageSync('account_user', user)
      return user
    })
}
function clearAccount() {
  wx.removeStorageSync('account_token')
  wx.removeStorageSync('account_user')
}
function currentUser() {
  if (!wx.getStorageSync('account_token')) return Promise.resolve(null)
  return request('/v1/users/me').then(user => {
    wx.setStorageSync('account_user', user)
    return user
  })
}
function logout() {
  if (!wx.getStorageSync('account_token')) return Promise.resolve()
  return request('/v1/auth/logout', { method: 'POST' }).catch(() => {}).then(clearAccount)
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
module.exports = { request, createPayment, verifyIdentity, wechatLogin, currentUser, logout, clearAccount, uploadAvatar }
