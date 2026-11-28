<template>
<view class="page page--no-tab community-editor">
  <view class="heading">{{ id ? '编辑动态' : '分享此刻' }}</view>
  <view class="media-head"><text class="media-head__title">图片</text><text class="media-head__hint">{{ images.length }}/9 · 可选</text></view>
  <view class="media-grid">
    <view v-for="(item, index) in images" :key="item.id" class="photo">
      <image class="photo__image" :src="item.path" mode="aspectFill"/>
      <button class="photo__delete" :data-index="index" @tap="removeImage" aria-label="删除图片">×</button>
    </view>
    <button v-if="images.length < 9" class="media-add" @tap="addImage" aria-label="添加图片">
      <view class="media-add__icon" aria-hidden="true"></view>
      <text class="media-add__title">添加图片</text>
    </button>
  </view>
  <input class="field" :value="title" maxlength="200" placeholder="标题（选填）" @input="title=$event.detail.value" />
  <text class="public-hint">发布后所有人可见，也会展示在社区；草稿仅自己可见。</text>
  <textarea class="body" :value="body" maxlength="5000" placeholder="分享你的想法、体验或问题" @input="body=$event.detail.value" />
</view>
<view class="bottom-actions">
  <button class="btn" @tap="save('draft')" :disabled="saving || loading || loadError">保存草稿</button>
  <button class="btn btn--primary" @tap="save('published')" :disabled="saving || loading || loadError">发布</button>
</view>
</template>
<script>
import { request, uploadCommunityImage, mediaUrl } from '../../services/api'
import { get } from '../../utils/store'
export default {
  data() {
    return { id: '', title: '', body: '', saving: false, loading: false, loadError: false, images: [], originalMediaIds: [] }
  },
  onLoad(options) {
    if (!uni.getStorageSync('account_token')) return uni.switchTab({ url: '/pages/profile/index' })
    this.id = options.id || ''
    if (!this.id) return
    this.loading = true
    request('/v1/posts/mine').then(rows => {
      const post = rows.find(item => item.id === this.id)
      if (!post) throw new Error('动态不存在或无权编辑')
      const images = (post.images || []).map((item, index) => ({
        id: item.id || `saved-${index}`,
        path: mediaUrl(item.url),
        remoteUrl: item.url,
        remote: true
      }))
      this.title = post.title
      this.body = post.body
      this.images = images
      this.originalMediaIds = images.map(item => item.id)
    }).catch(error => { this.loadError = true; uni.showToast({ title: error.message, icon: 'none' }) }).finally(() => { this.loading = false })
  },
  methods: {
    addImage() {
      const remaining = 9 - this.images.length
      if (remaining <= 0) return uni.showToast({ title: '最多上传9张图片', icon: 'none' })
      uni.chooseMedia({
        count: remaining,
        mediaType: ['image'],
        sourceType: ['album', 'camera'],
        success: ({ tempFiles }) => {
          const additions = tempFiles.map((file, index) => ({
            id: `local-${Date.now()}-${index}`,
            path: file.tempFilePath,
            remote: false
          }))
          this.images = this.images.concat(additions)
        }
      })
    },
    removeImage(event) {
      const selected = Number(event.currentTarget.dataset.index)
      this.images = this.images.filter((_, index) => index !== selected)
    },
    async syncImages(postId) {
      for (const mediaId of [...this.originalMediaIds]) {
        if (!this.images.some(image => image.id === mediaId)) {
          await request(`/v1/posts/${encodeURIComponent(postId)}/images/${encodeURIComponent(mediaId)}`, { method: 'DELETE' })
          this.originalMediaIds = this.originalMediaIds.filter(id => id !== mediaId)
        }
      }
      for (const image of this.images) {
        if (image.remote) continue
        const uploaded = await uploadCommunityImage(postId, image.path)
        image.id = uploaded.id || image.id
        image.remoteUrl = uploaded.url
        image.path = mediaUrl(uploaded.url)
        image.remote = true
      }
      this.originalMediaIds = this.images.map(image => image.id)
    },
    async save(status) {
      if (this.saving || this.loading || this.loadError) return
      if (!this.body.trim()) return uni.showToast({ title: '写一点此刻的想法吧', icon: 'none' })
      this.saving = true
      try {
        const payload = { title: this.title.trim() || this.body.trim().slice(0, 40), body: this.body.trim(), city: get('currentCity', '上海'), status: 'draft' }
        const post = await request(this.id ? `/v1/posts/${this.id}` : '/v1/posts', {
          method: this.id ? 'PUT' : 'POST',
          data: payload
        })
        this.id = post.id || this.id
        await this.syncImages(this.id)
        if (status === 'published') await request(`/v1/posts/${encodeURIComponent(this.id)}`, { method: 'PUT', data: { ...payload, status } })
        uni.showToast({ title: status === 'draft' ? '草稿已保存' : '发布成功' })
        setTimeout(() => uni.navigateBack(), 300)
      } catch (error) {
        uni.showToast({ title: error.message, icon: 'none' })
      } finally {
        this.saving = false
      }
    }
  }
}
</script>
<style scoped>
.public-hint { display: block; margin-bottom: 20rpx; color: #91819f; font-size: 23rpx; line-height: 1.6; }
.community-editor { padding-top: 28rpx; }
.heading { margin-bottom: 28rpx; font-size: var(--type-page); line-height: 1.25; font-weight: var(--weight-page); }
.media-head { display: flex; align-items: baseline; justify-content: space-between; gap: 16rpx; margin-bottom: 16rpx; }
.media-head__title { font-size: 28rpx; font-weight: 700; }
.media-head__hint { color: var(--color-muted); font-size: 22rpx; }
.media-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14rpx; margin-bottom: 24rpx; }
.photo, .media-add { position: relative; aspect-ratio: 1; min-width: 0; overflow: hidden; box-sizing: border-box; border-radius: 20rpx; }
.photo { background: #f1eff5; }
.photo__image { width: 100%; height: 100%; }
.photo__delete { position: absolute; right: 7rpx; top: 7rpx; width: 46rpx; height: 46rpx; margin: 0; padding: 0; border: 0; border-radius: 50%; background: rgba(26,21,33,.72); color: #fff; font-size: 31rpx; line-height: 42rpx; }
.media-add { margin: 0; padding: 16rpx 8rpx; border: 2rpx dashed #cbc5d1; background: #f8f7fa; color: var(--color-muted); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; }
.media-add__icon { position: relative; width: 52rpx; height: 40rpx; box-sizing: border-box; border: 4rpx solid var(--color-muted); border-radius: 10rpx; }
.media-add__icon::before { content: ''; position: absolute; left: 18rpx; top: -12rpx; width: 4rpx; height: 22rpx; border-radius: 4rpx; background: var(--color-muted); }
.media-add__icon::after { content: ''; position: absolute; left: 9rpx; top: -3rpx; width: 22rpx; height: 4rpx; border-radius: 4rpx; background: var(--color-muted); }
.media-add__title { color: #3e3747; font-size: 22rpx; font-weight: 700; }
.field, .body {
  width: 100%;
  box-sizing: border-box;
  margin-bottom: 20rpx;
  padding: 22rpx 24rpx;
  border: 1rpx solid var(--color-border);
  border-radius: 20rpx;
  background: var(--color-surface);
  color: var(--color-text);
  font-size: var(--type-body);
}
.body { height: 360rpx; line-height: 1.55; }
.bottom-actions .btn { margin: 0; line-height: 1; }
.bottom-actions .btn--primary { flex: 1.45; }
</style>
