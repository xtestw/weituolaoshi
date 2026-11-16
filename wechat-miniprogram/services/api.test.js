const assert = require('node:assert/strict')
const storage = {}
let nextResponse = { statusCode: 200, data: {} }
let requested = null

global.getApp = () => ({ globalData: { apiBaseUrl: 'http://127.0.0.1:8080', useDemoAccount: false } })
global.wx = {
  login: ({ success }) => success({ code: 'fresh-code' }),
  request: (options) => { requested = options; options.success(nextResponse) },
  getStorageSync: (key) => storage[key],
  setStorageSync: (key, value) => { storage[key] = value },
  removeStorageSync: (key) => { delete storage[key] }
}

const api = require('./api')

async function run() {
  nextResponse = { statusCode: 200, data: { token: 'token-1', user: { id: 'user-1', nickname: '微信用户' } } }
  const user = await api.wechatLogin()
  assert.equal(requested.url, 'http://127.0.0.1:8080/v1/auth/wechat-login')
  assert.equal(requested.data.code, 'fresh-code')
  assert.equal(storage.account_token, 'token-1')
  assert.equal(user.id, 'user-1')

  nextResponse = { statusCode: 200, data: { id: 'user-1', nickname: '更新后的昵称' } }
  assert.equal((await api.currentUser()).nickname, '更新后的昵称')
  assert.equal(storage.account_user.nickname, '更新后的昵称')

  nextResponse = { statusCode: 401, data: { message: '登录已过期' } }
  await assert.rejects(api.currentUser(), /登录已过期/)
  assert.equal(storage.account_token, undefined)
  assert.equal(storage.account_user, undefined)

  nextResponse = { statusCode: 200, data: { token: 'token-2', user: { id: 'user-1' } } }
  await api.wechatLogin()
  nextResponse = { statusCode: 200, data: { ok: true } }
  await api.logout()
  assert.equal(storage.account_token, undefined)
  assert.equal(storage.account_user, undefined)
  assert.equal(requested.url, 'http://127.0.0.1:8080/v1/auth/logout')
}

run().catch(error => { console.error(error); process.exitCode = 1 })
