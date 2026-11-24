const { request } = require('../../services/api')
Page({
  data: { posts: [] },
  onShow() { request('/v1/posts/mine').then(posts => this.setData({ posts })).catch(error => wx.showToast({ title: error.message, icon: 'none' })) },
  create() { wx.navigateTo({ url: '/pages/community-editor/index' }) },
  open(e) { const post = this.data.posts[e.currentTarget.dataset.index]; wx.navigateTo({ url: post.status === 'draft' ? `/pages/community-editor/index?id=${post.id}` : `/pages/community-detail/index?id=${post.id}` }) },
  edit(e) { wx.navigateTo({ url: `/pages/community-editor/index?id=${this.data.posts[e.currentTarget.dataset.index].id}` }) },
  remove(e) { const post = this.data.posts[e.currentTarget.dataset.index]; wx.showModal({ title: '删除帖子？', success: async ({ confirm }) => { if (!confirm) return; try { await request(`/v1/posts/${post.id}`, { method: 'DELETE' }); this.setData({ posts: this.data.posts.filter(item => item.id !== post.id) }) } catch(error) { wx.showToast({ title: error.message, icon: 'none' }) } } }) }
})
