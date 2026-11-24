<template>
<view class="page" v-if="post">
  <text class="kind">普通用户 · 社区帖</text>
  <view class="title">{{ post.title }}</view>
  <text class="author">{{ post.author || '用户' }} · {{ post.createdAt }}</text>
  <view v-if="imageUrls.length" class="gallery">
    <image v-for="(src, index) in imageUrls" :key="index" class="gallery__image" :src="src" mode="aspectFill" :data-index="index" @tap="previewImage"/>
  </view>
  <view class="body">{{ post.body }}</view>
  <view class="actions">
    <button class="like" :class="post.liked ? 'like--on' : ''" @tap="toggleLike">{{ post.liked ? '已赞' : '点赞' }} · {{ post.likeCount || 0 }}</button>
  </view>
  <view class="comments-head">评论 · {{ comments.length }}</view>
  <view v-if="!comments.length" class="empty">还没有评论，来抢沙发</view>
  <view v-for="item in comments" :key="item.id" class="comment">
    <view class="comment__meta"><text>{{ item.author || '用户' }}</text><text>{{ item.createdAt }}</text></view>
    <view class="comment__body">{{ item.body }}</view>
    <button v-if="item.authorId === meId" class="comment__delete" :data-id="item.id" @tap="removeComment">删除</button>
  </view>
  <view class="composer">
    <input class="composer__input" :value="draft" maxlength="1000" placeholder="说点什么…" @input="draft=$event.detail.value" />
    <button class="composer__send" :disabled="sending" @tap="sendComment">发送</button>
  </view>
</view>
</template>
<script>
import { request, mediaUrl } from '../../services/api'
export default {
  data() {
    return { post: null, comments: [], draft: '', sending: false, meId: '', postId: '' }
  },
  computed: {
    imageUrls() {
      return ((this.post && this.post.images) || []).map(item => mediaUrl(item.url || item)).filter(Boolean)
    }
  },
  onLoad(options) {
    this.postId = options.id || ''
    const user = uni.getStorageSync('account_user') || {}
    this.meId = user.id || ''
    this.reload()
  },
  onShareAppMessage() {
    return { title: this.post ? this.post.title : '社区帖子', path: this.post ? `/pages/community-detail/index?id=${this.post.id}` : '/pages/home/index' }
  },
  methods: {
    previewImage(event) {
      const current = this.imageUrls[Number(event.currentTarget.dataset.index)]
      uni.previewImage({ current, urls: this.imageUrls })
    },
    async reload() {
      try {
        const [post, comments] = await Promise.all([
          request(`/v1/posts/${encodeURIComponent(this.postId)}`),
          request(`/v1/posts/${encodeURIComponent(this.postId)}/comments`)
        ])
        this.post = post
        this.comments = comments
      } catch (error) {
        uni.showToast({ title: error.message, icon: 'none' })
      }
    },
    async toggleLike() {
      if (!uni.getStorageSync('account_token')) return uni.switchTab({ url: '/pages/profile/index' })
      try {
        this.post = await request(`/v1/posts/${encodeURIComponent(this.postId)}/likes`, { method: this.post.liked ? 'DELETE' : 'POST' })
      } catch (error) {
        uni.showToast({ title: error.message, icon: 'none' })
      }
    },
    async sendComment() {
      if (!uni.getStorageSync('account_token')) return uni.switchTab({ url: '/pages/profile/index' })
      const body = this.draft.trim()
      if (!body) return uni.showToast({ title: '请输入评论', icon: 'none' })
      this.sending = true
      try {
        const comment = await request(`/v1/posts/${encodeURIComponent(this.postId)}/comments`, { method: 'POST', data: { body } })
        this.comments = this.comments.concat(comment)
        this.draft = ''
        this.post = { ...this.post, commentCount: (this.post.commentCount || 0) + 1 }
      } catch (error) {
        uni.showToast({ title: error.message, icon: 'none' })
      } finally {
        this.sending = false
      }
    },
    removeComment(event) {
      const commentId = event.currentTarget.dataset.id
      uni.showModal({
        title: '删除这条评论？',
        confirmColor: '#c9384f',
        success: async ({ confirm }) => {
          if (!confirm) return
          try {
            await request(`/v1/posts/${encodeURIComponent(this.postId)}/comments/${encodeURIComponent(commentId)}`, { method: 'DELETE' })
            this.comments = this.comments.filter(item => item.id !== commentId)
            this.post = { ...this.post, commentCount: Math.max(0, (this.post.commentCount || 1) - 1) }
          } catch (error) {
            uni.showToast({ title: error.message, icon: 'none' })
          }
        }
      })
    }
  }
}
</script>
<style>
.page { padding: 36rpx 36rpx 160rpx; }
.kind { color: #6d28d9; font-size: 24rpx; }
.title { margin: 22rpx 0; font-size: 42rpx; font-weight: 700; }
.author { color: #777; font-size: 24rpx; }
.gallery { margin-top: 28rpx; display: grid; grid-template-columns: repeat(3, 1fr); gap: 12rpx; }
.gallery__image { width: 100%; aspect-ratio: 1; border-radius: 16rpx; background: #f1eff5; }
.body { margin-top: 36rpx; white-space: pre-wrap; line-height: 1.7; }
.actions { margin-top: 28rpx; }
.like { margin: 0; min-height: 72rpx; padding: 0 28rpx; display: inline-flex; align-items: center; border-radius: 999rpx; background: #f3e8ff; color: #6d28d9; font-size: 24rpx; font-weight: 700; }
.like--on { background: #6d28d9; color: #fff; }
.comments-head { margin: 40rpx 0 18rpx; font-size: 30rpx; font-weight: 700; }
.empty { color: #777; font-size: 24rpx; padding: 12rpx 0 24rpx; }
.comment { position: relative; padding: 20rpx 0; border-bottom: 1rpx solid #eee; }
.comment__meta { display: flex; justify-content: space-between; gap: 16rpx; color: #777; font-size: 22rpx; }
.comment__body { margin-top: 10rpx; line-height: 1.6; white-space: pre-wrap; }
.comment__delete { position: absolute; right: 0; bottom: 12rpx; margin: 0; padding: 0 8rpx; background: transparent; color: #c9384f; font-size: 22rpx; }
.composer { position: fixed; left: 0; right: 0; bottom: 0; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); display: flex; gap: 12rpx; background: #fff; border-top: 1rpx solid #eee; }
.composer__input { flex: 1; min-height: 72rpx; padding: 0 20rpx; border-radius: 16rpx; background: #f6f5f8; }
.composer__send { margin: 0; min-width: 120rpx; min-height: 72rpx; padding: 0 20rpx; border-radius: 16rpx; background: #6d28d9; color: #fff; font-size: 26rpx; }
</style>
