const app = () => getApp()

function request(path, options = {}) {
  const base = app().globalData.apiBaseUrl
  if (!base) return Promise.reject(new Error('API_NOT_CONFIGURED'))
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${base}${path}`,
      method: options.method || 'GET',
      data: options.data,
      header: { 'content-type': 'application/json', ...(options.header || {}) },
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

module.exports = { request, createPayment, verifyIdentity }
