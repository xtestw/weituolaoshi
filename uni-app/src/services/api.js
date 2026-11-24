const app = () => getApp()

function request(path, options = {}) {
  const base = app().globalData.apiBaseUrl
  if (!base) return Promise.reject(new Error('API_NOT_CONFIGURED'))
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${base}${path}`,
      method: options.method || 'GET',
      data: options.data,
      header: { 'content-type': 'application/json', ...(uni.getStorageSync('account_token') ? { Authorization: `Bearer ${uni.getStorageSync('account_token')}` } : {}), ...(options.header || {}) },
      success: ({ statusCode, data }) => {
        if (statusCode >= 200 && statusCode < 300) resolve(data)
        else {
          if (statusCode === 401 && uni.getStorageSync('account_token')) clearAccount()
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
      uni.requestPayment({ ...payment, success: resolve, fail: reject })
    }))
}

function verifyIdentity(payload) {
  return request('/v1/identity/verifications', { method: 'POST', data: payload })
}

function wechatLogin() {
  if (app().globalData.useDemoAccount) {
    return request('/v1/dev/login', { method: 'POST' }).then(({ token, user }) => {
      uni.setStorageSync('account_token', token)
      uni.setStorageSync('account_user', user)
      return user
    })
  }
  return new Promise((resolve, reject) => uni.login({ success: resolve, fail: reject }))
    .then(({ code }) => {
      if (!code) throw new Error('未获取到微信登录凭证，请重试')
      return request('/v1/auth/wechat-login', { method: 'POST', data: { code } })
    })
    .then(({ token, user }) => {
      if (!token || !user) throw new Error('登录响应不完整，请重试')
      uni.setStorageSync('account_token', token)
      uni.setStorageSync('account_user', user)
      return user
    })
}
function clearAccount() {
  uni.removeStorageSync('account_token')
  uni.removeStorageSync('account_user')
}
function currentUser() {
  if (!uni.getStorageSync('account_token')) return Promise.resolve(null)
  return request('/v1/users/me').then(user => {
    uni.setStorageSync('account_user', user)
    return user
  })
}
function logout() {
  if (!uni.getStorageSync('account_token')) return Promise.resolve()
  return request('/v1/auth/logout', { method: 'POST' }).catch(() => {}).then(clearAccount)
}
function uploadAvatar(filePath) {
  const base = app().globalData.apiBaseUrl
  const token = uni.getStorageSync('account_token')
  if (!base || !token) return Promise.reject(new Error('请先登录'))
  return new Promise((resolve, reject) => uni.uploadFile({
    url: `${base}/v1/users/me/avatar`, filePath, name: 'file',
    header: { Authorization: `Bearer ${token}` },
    success: ({ statusCode, data }) => statusCode >= 200 && statusCode < 300 ? resolve(JSON.parse(data)) : reject(new Error('头像上传失败')),
    fail: reject
  }))
}
function uploadServiceImage(serviceId, filePath) {
  const base = app().globalData.apiBaseUrl
  const token = uni.getStorageSync('account_token')
  if (!base || !token) return Promise.reject(new Error('请先登录'))
  return new Promise((resolve, reject) => uni.uploadFile({
    url: `${base}/v1/services/${encodeURIComponent(serviceId)}/images`, filePath, name: 'file',
    header: { Authorization: `Bearer ${token}` },
    success: ({ statusCode, data }) => {
      let parsed = {}
      try { parsed = JSON.parse(data) } catch (_) {}
      statusCode >= 200 && statusCode < 300 ? resolve(parsed) : reject(new Error(parsed.message || '图片上传失败'))
    }, fail: reject
  }))
}
function uploadCommunityImage(postId, filePath) {
  const base = app().globalData.apiBaseUrl
  const token = uni.getStorageSync('account_token')
  if (!base || !token) return Promise.reject(new Error('请先登录'))
  return new Promise((resolve, reject) => uni.uploadFile({
    url: `${base}/v1/posts/${encodeURIComponent(postId)}/images`, filePath, name: 'file',
    header: { Authorization: `Bearer ${token}` },
    success: ({ statusCode, data }) => {
      let parsed = {}
      try { parsed = JSON.parse(data) } catch (_) {}
      statusCode >= 200 && statusCode < 300 ? resolve(parsed) : reject(new Error(parsed.message || '图片上传失败'))
    }, fail: reject
  }))
}
function mediaUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//.test(url) || url.startsWith('/')) {
    const base = app().globalData.apiBaseUrl || ''
    return url.startsWith('/') ? `${base}${url}` : url
  }
  return url
}
export { request, createPayment, verifyIdentity, wechatLogin, currentUser, logout, clearAccount, uploadAvatar, uploadServiceImage, uploadCommunityImage, mediaUrl }
