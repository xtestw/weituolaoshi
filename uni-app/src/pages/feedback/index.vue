<template>
<view class="feedback-page">
  <view v-if="submitted" class="feedback-card success"><text class="feedback-title">反馈已提交</text><text class="feedback-hint">感谢你的反馈，我们会认真查看。</text><button class="submit" @tap="back">返回</button></view>
  <template v-else>
    <view class="feedback-card"><text class="feedback-title">告诉我们你的建议或遇到的问题</text><text class="feedback-hint">请尽量描述操作步骤和问题现象，帮助我们改进。</text><textarea :value="body" maxlength="2000" :disabled="submitting" @input="onBody" placeholder="请输入反馈内容（必填）"/><text class="counter">{{body.length}} / 2000</text></view>
    <view class="feedback-card"><text class="feedback-title">添加截图（选填）</text><text class="feedback-hint">最多3张，每张不超过5MB，支持PNG、JPEG、WebP。</text><view class="screenshots"><view v-for="(item, index) in images" :key="item.path" class="screenshot"><image :src="item.path" mode="aspectFill" :data-path="item.path" @tap="preview"/><button :disabled="submitting" :data-index="index" @tap="removeImage" aria-label="移除截图">移除</button></view><button v-if="images.length < 3" class="add-image" :disabled="submitting" @tap="chooseImages">＋ 添加截图</button></view></view>
    <view class="feedback-card"><text class="feedback-title">联系方式（选填）</text><input :value="contact" maxlength="100" :disabled="submitting" @input="onContact" placeholder="手机号、微信号或邮箱"/></view>
    <text v-if="error" class="feedback-error">{{error}}</text><button class="submit" :loading="submitting" :disabled="submitting" @tap="submit">{{submitting ? '正在提交…' : '提交反馈'}}</button>
  </template>
</view>
</template>
<script>
import { request } from '../../services/api'
export default {
  data() { return { body: '', contact: '', images: [], submitting: false, submitted: false, error: '' } },
  onLoad() { this.requestId = `feedback-${Date.now()}-${Math.random().toString(36).slice(2)}` },
  methods: {
  setData(values) { Object.assign(this, values) },
  onBody(e) { this.setData({ body: e.detail.value }) },
  onContact(e) { this.setData({ contact: e.detail.value }) },
  chooseImages() {
    if (this.submitting) return
    wx.chooseMedia({ count: 3 - this.images.length, mediaType: ['image'], sourceType: ['album', 'camera'], success: result => {
      const files = result.tempFiles
      if (files.some(file => file.size > 5 * 1024 * 1024)) return this.setData({ error: '每张截图不能超过5MB，请重新选择' })
      this.setData({ images: this.images.concat(files.map(file => ({ path: file.tempFilePath, id: '' }))).slice(0, 3), error: '' })
    }, fail: error => { if (!(error.errMsg || '').includes('cancel')) this.setData({ error: '无法选择截图，请重试' }) } })
  },
  removeImage(e) {
    if (this.submitting) return
    const index = Number(e.currentTarget.dataset.index)
    const image = this.images[index]
    this.setData({ images: this.images.filter((_, i) => i !== index) })
    if (image.id) request('/v1/feedback/images/' + image.id, { method: 'DELETE' }).catch(() => {})
  },
  preview(e) { uni.previewImage({ current: e.currentTarget.dataset.path, urls: this.images.map(image => image.path) }) },
  upload(filePath) {
    return new Promise((resolve, reject) => uni.uploadFile({
      url: getApp().globalData.apiBaseUrl + '/v1/feedback/images', filePath, name: 'file',
      header: { Authorization: `Bearer ${uni.getStorageSync('account_token')}` },
      success: ({ statusCode, data }) => {
        let parsed = {}; try { parsed = JSON.parse(data) } catch (_) {}
        if (statusCode >= 200 && statusCode < 300 && parsed.id) resolve(parsed.id)
        else reject(new Error(parsed.message || (statusCode === 401 ? '登录已过期，请返回“我的”重新登录' : '截图上传失败，请重试')))
      }, fail: reject
    }))
  },
  async submit() {
    if (this.submitting || this.submitted) return
    if (!uni.getStorageSync('account_token')) return this.setData({ error: '请返回“我的”登录后提交反馈' })
    const body = this.body.trim(), contact = this.contact.trim()
    if (!body || body.length > 2000) return this.setData({ error: '请填写1至2000字的反馈内容' })
    this.setData({ submitting: true, error: '' })
    try {
      const images = this.images.map(image => ({ ...image }))
      for (const image of images) {
        if (!image.id) image.id = await this.upload(image.path)
        this.setData({ images: images.map(item => ({ ...item })) })
      }
      await request('/v1/feedback', { method: 'POST', data: { body, contact, imageIds: images.map(image => image.id), requestId: this.requestId } })
      this.setData({ submitted: true })
    } catch (error) { this.setData({ error: error.message || '提交失败，请检查网络后重试，已填写内容会保留' }) }
    finally { this.setData({ submitting: false }) }
  },
  back() { uni.navigateBack() }
}
}
</script>
<style>
.feedback-page { min-height:100vh; box-sizing:border-box; padding:28rpx 28rpx calc(40rpx + env(safe-area-inset-bottom)); background:#f7f6fb; }
.feedback-card { background:#fff; border-radius:24rpx; padding:28rpx; margin-bottom:24rpx; }
.feedback-title { display:block; font-size:30rpx; font-weight:650; color:#25202d; }
.feedback-hint { display:block; font-size:24rpx; line-height:1.6; color:#827b8a; margin:14rpx 0 24rpx; }
.feedback-card textarea { width:100%; height:300rpx; font-size:28rpx; line-height:1.6; }
.counter { display:block; text-align:right; font-size:22rpx; color:#827b8a; }
.feedback-card input { margin-top:24rpx; height:70rpx; font-size:28rpx; }
.screenshots { display:flex; gap:16rpx; flex-wrap:wrap; }
.screenshot { width:180rpx; }
.screenshot image { width:180rpx; height:180rpx; border-radius:16rpx; }
.screenshot button { font-size:24rpx; color:#b42339; background:#fff; }
.add-image { margin:0; width:180rpx; height:180rpx; display:flex; align-items:center; justify-content:center; font-size:24rpx; background:#f5f0ff; color:#7244b8; }
.submit { background:#7244b8; color:#fff; border-radius:22rpx; margin-top:24rpx; font-size:30rpx; }
.feedback-error { display:block; color:#b42339; font-size:26rpx; margin:20rpx 0; }
.success { text-align:center; padding:60rpx 28rpx; }
</style>
