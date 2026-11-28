const { request } = require('../../services/api')
Page({
  data: { body: '', contact: '', images: [], submitting: false, submitted: false, error: '' },
  onLoad() { this.requestId = `feedback-${Date.now()}-${Math.random().toString(36).slice(2)}` },
  onBody(e) { this.setData({ body: e.detail.value }) },
  onContact(e) { this.setData({ contact: e.detail.value }) },
  chooseImages() {
    if (this.data.submitting) return
    wx.chooseMedia({ count: 3 - this.data.images.length, mediaType: ['image'], sourceType: ['album', 'camera'], success: result => {
      const files = result.tempFiles
      if (files.some(file => file.size > 5 * 1024 * 1024)) return this.setData({ error: '每张截图不能超过5MB，请重新选择' })
      this.setData({ images: this.data.images.concat(files.map(file => ({ path: file.tempFilePath, id: '' }))).slice(0, 3), error: '' })
    }, fail: error => { if (!(error.errMsg || '').includes('cancel')) this.setData({ error: '无法选择截图，请重试' }) } })
  },
  removeImage(e) {
    if (this.data.submitting) return
    const index = Number(e.currentTarget.dataset.index)
    const image = this.data.images[index]
    this.setData({ images: this.data.images.filter((_, i) => i !== index) })
    if (image.id) request('/v1/feedback/images/' + image.id, { method: 'DELETE' }).catch(() => {})
  },
  preview(e) { wx.previewImage({ current: e.currentTarget.dataset.path, urls: this.data.images.map(image => image.path) }) },
  upload(filePath) {
    return new Promise((resolve, reject) => wx.uploadFile({
      url: getApp().globalData.apiBaseUrl + '/v1/feedback/images', filePath, name: 'file',
      header: { Authorization: `Bearer ${wx.getStorageSync('account_token')}` },
      success: ({ statusCode, data }) => {
        let parsed = {}; try { parsed = JSON.parse(data) } catch (_) {}
        if (statusCode >= 200 && statusCode < 300 && parsed.id) resolve(parsed.id)
        else reject(new Error(parsed.message || (statusCode === 401 ? '登录已过期，请返回“我的”重新登录' : '截图上传失败，请重试')))
      }, fail: reject
    }))
  },
  async submit() {
    if (this.data.submitting || this.data.submitted) return
    if (!wx.getStorageSync('account_token')) return this.setData({ error: '请返回“我的”登录后提交反馈' })
    const body = this.data.body.trim(), contact = this.data.contact.trim()
    if (!body || body.length > 2000) return this.setData({ error: '请填写1至2000字的反馈内容' })
    this.setData({ submitting: true, error: '' })
    try {
      const images = this.data.images.map(image => ({ ...image }))
      for (const image of images) {
        if (!image.id) image.id = await this.upload(image.path)
        this.setData({ images: images.map(item => ({ ...item })) })
      }
      await request('/v1/feedback', { method: 'POST', data: { body, contact, imageIds: images.map(image => image.id), requestId: this.requestId } })
      this.setData({ submitted: true })
    } catch (error) { this.setData({ error: error.message || '提交失败，请检查网络后重试，已填写内容会保留' }) }
    finally { this.setData({ submitting: false }) }
  },
  back() { wx.navigateBack() }
})
