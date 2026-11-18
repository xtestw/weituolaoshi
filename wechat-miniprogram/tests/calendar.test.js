const assert = require('node:assert/strict')
const { test } = require('node:test')

let page
let open = false
let failUpdate = false
let lastRequest
global.Page = definition => {
  page = { ...definition, data: { ...definition.data }, setData(values) { Object.assign(this.data, values) } }
}
global.getApp = () => ({ globalData: { apiBaseUrl: 'http://127.0.0.1:8080' } })
global.wx = {
  getStorageSync: () => 'test-token',
  showToast: () => {},
  request(options) {
    lastRequest = options
    if (failUpdate && options.method === 'PUT') {
      options.success({ statusCode: 409, data: { message: '已有订单占用' } })
      return
    }
    if (options.method === 'PUT') open = options.data.open
    if (options.method === 'POST') open = true
    options.success({ statusCode: 200, data: options.method === 'GET'
      ? { days: [{ date: `${page.data.year}-${String(page.data.month).padStart(2, '0')}-28`, open }] }
      : {} })
  }
}
require('../pages/b-calendar/index.js')

test('calendar reads and updates server availability', async () => {
  await page.loadMonth(page.data.monthIndex + 1)
  assert.equal(lastRequest.method, 'GET')
  const key = `${page.data.year}-${String(page.data.month).padStart(2, '0')}-28`
  let day = page.data.days.find(item => item.key === key)
  assert.equal(day.open, false)

  await page.toggleDay({ currentTarget: { dataset: { key } } })
  day = page.data.days.find(item => item.key === key)
  assert.equal(day.open, true)
  assert.equal(page.data.error, '')

  failUpdate = true
  await page.toggleDay({ currentTarget: { dataset: { key } } })
  assert.equal(page.data.days.find(item => item.key === key).open, true)
  assert.match(page.data.error, /已有订单占用/)
  failUpdate = false

  await page.applyBatch({ currentTarget: { dataset: { index: 0 } } })
  assert.equal(page.data.lastBatchIndex, 0)
  assert.equal(page.data.days.find(item => item.key === key).open, true)
})
