const { request } = require('../../services/api')
Page({
  data: { post: null },
  onLoad(options) { request(`/v1/posts/${encodeURIComponent(options.id || '')}`).then(post => this.setData({ post })).catch(error => wx.showToast({ title: error.message, icon: 'none' })) },
  onShareAppMessage() { return { title: this.data.post ? this.data.post.title : '社区帖子', path: this.data.post ? `/pages/community-detail/index?id=${this.data.post.id}` : '/pages/home/index' } }
})
