const assert = require('node:assert/strict')
const { test } = require('node:test')

const storage = { account_token: 'test-token' }
let response = { statusCode: 503, data: { message: '实名核验服务尚未配置' } }
let page
global.Page = definition => {
  page = { ...definition, data: { ...definition.data }, setData(values) { Object.assign(this.data, values) } }
}
global.getApp = () => ({ globalData: { apiBaseUrl: 'http://127.0.0.1:8080' } })
global.wx = {
  getStorageSync: key => storage[key],
  setStorageSync: (key, value) => { storage[key] = value },
  removeStorageSync: key => { delete storage[key] },
  request: options => options.success(response),
  showToast: () => {},
  navigateBack: () => {}
}
require('../pages/verify/index.js')

const prefix = '11010119900101002'
const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
const checksum = '10X98765432'[prefix.split('').reduce((sum, digit, index) => sum + Number(digit) * weights[index], 0) % 11]
const idNumber = prefix + checksum

test('identity page never marks verification successful on provider failure', async () => {
  Object.assign(page.data, { name: '测试用户', idNumber, agreed: true, mode: 'customer' })
  await page.submit()
  assert.match(page.data.errors.submit, /尚未配置/)
  assert.equal(storage.coser_v1_verification, undefined)

  response = { statusCode: 200, data: { status: 'verified', adult: true, gender: 'F' } }
  await page.submit()
  assert.equal(storage.coser_v1_verification.verified, true)
  assert.equal(page.data.idNumber, '')
  assert.equal(JSON.stringify(storage).includes(idNumber), false)
})
