const { request } = require('../../services/api')
const { get } = require('../../utils/store')
Page({
  data: { id: '', title: '', body: '', saving: false },
  onLoad(options) {
    const id = options.id || ''
    this.setData({ id })
    if (id) request('/v1/posts/mine').then(rows => {
      const post = rows.find(item => item.id === id)
      if (post) this.setData({ title: post.title, body: post.body })
    }).catch(error => wx.showToast({ title: error.message, icon: 'none' }))
  },
  onTitle(e) { this.setData({ title: e.detail.value }) },
  onBody(e) { this.setData({ body: e.detail.value }) },
  draft() { this.save('draft') },
  publish() { this.save('published') },
  async save(status) {
    if (!this.data.title.trim() || !this.data.body.trim()) return wx.showToast({ title: '请填写标题和正文', icon: 'none' })
    this.setData({ saving: true })
    try {
      await request(this.data.id ? `/v1/posts/${this.data.id}` : '/v1/posts', { method: this.data.id ? 'PUT' : 'POST', data: { title: this.data.title.trim(), body: this.data.body.trim(), city: get('currentCity', '上海'), status } })
      wx.showToast({ title: status === 'draft' ? '草稿已保存' : '发布成功' })
      setTimeout(() => wx.navigateBack(), 300)
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
    finally { this.setData({ saving: false }) }
  }
})
