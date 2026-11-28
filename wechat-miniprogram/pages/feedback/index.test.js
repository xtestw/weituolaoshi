const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')

function page(request) {
  let definition
  vm.runInNewContext(fs.readFileSync(__dirname + '/index.js', 'utf8'), {
    require: () => ({ request }), Page: value => { definition = value },
    wx: { getStorageSync: () => 'token' }, Date, Math
  })
  const instance = { ...definition, data: { ...definition.data }, setData(values) { Object.assign(this.data, values) } }
  instance.onLoad()
  return instance
}

test('failed submission retains uploaded images and retries with the same request ID', async () => {
  const requests = []
  const instance = page(async (path, options) => {
    requests.push({ path, options })
    if (requests.length === 1) throw new Error('network error')
    return { id: 'feedback-id' }
  })
  let uploads = 0
  instance.upload = async () => { uploads++; return 'image-id' }
  instance.data.body = '页面加载有问题'
  instance.data.images = [{ path: 'local.png', id: '' }]
  await instance.submit()
  assert.equal(instance.data.submitted, false)
  assert.equal(instance.data.body, '页面加载有问题')
  assert.equal(instance.data.images[0].id, 'image-id')
  await instance.submit()
  assert.equal(uploads, 1)
  assert.equal(instance.data.submitted, true)
  assert.equal(requests[0].options.data.requestId, requests[1].options.data.requestId)
  await instance.submit()
  assert.equal(requests.length, 2)
})

test('blank content cannot be submitted and double taps cannot submit twice', async () => {
  let calls = 0, finish
  const instance = page(() => { calls++; return new Promise(resolve => { finish = resolve }) })
  instance.data.body = '  '
  await instance.submit()
  assert.equal(calls, 0)
  instance.data.body = '建议增加搜索提示'
  const pending = instance.submit()
  await instance.submit()
  assert.equal(calls, 1)
  finish({ id: 'feedback-id' })
  await pending
  assert.equal(instance.data.submitted, true)
})
