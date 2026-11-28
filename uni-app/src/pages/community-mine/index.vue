<template>
<view class="page">
  <button class="primary" @tap="create">发布动态</button>
  <view v-for="post in posts" :key="post.id" class="item">
    <text class="title" @tap="open(post)">{{ post.title }}</text><text>{{ post.status === 'draft' ? '草稿' : '已发布' }}</text>
    <view class="actions"><button @tap="edit(post)">编辑</button><button @tap="remove(post)">删除</button></view>
  </view>
  <view v-if="!posts.length">还没有动态，分享你的第一条日常吧</view>
</view>
</template>
<script>
import { request } from '../../services/api'
export default {
  data() { return { posts: [] } },
  onShow() { request('/v1/posts/mine').then(rows => { this.posts = rows }).catch(error => uni.showToast({ title: error.message, icon: 'none' })) },
  methods: {
    create() { uni.navigateTo({ url: '/pages/community-editor/index' }) },
    open(post) { if (post.status === 'draft') return this.edit(post); uni.navigateTo({ url: `/pages/community-detail/index?id=${post.id}` }) },
    edit(post) { uni.navigateTo({ url: `/pages/community-editor/index?id=${post.id}` }) },
    remove(post) { uni.showModal({ title: '删除帖子？', success: async ({ confirm }) => { if (!confirm) return; try { await request(`/v1/posts/${post.id}`, { method: 'DELETE' }); this.posts = this.posts.filter(item => item.id !== post.id) } catch(error) { uni.showToast({ title: error.message, icon: 'none' }) } } }) }
  }
}
</script>
<style>
.page { padding: 32rpx; }.primary { background: #6d28d9; color: white; margin-bottom: 30rpx; }.item { padding: 28rpx; margin-bottom: 20rpx; background: white; border-radius: 20rpx; }.title { display: block; font-weight: 700; font-size: 32rpx; }.actions { display: flex; }.actions button { flex: 1; }
</style>
