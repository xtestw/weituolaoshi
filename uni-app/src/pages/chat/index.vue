<template>
<view class="chat-page">
  <view class="safety">请勿在站外支付；涉及改期或边界调整请在订单内留痕。</view>
  <scroll-view class="chat-list" scroll-y scroll-into-view="bottom">
    <view v-for="(item, index) in messages" :key="item.id" :class="&quot;bubble-row bubble-row--&quot; + (item.from)" :data-index="index" @tap="viewImage" @longpress="report"><view class="bubble">{{ item.text }}</view></view>
    <view id="bottom"></view>
  </scroll-view>
  <view v-if="isCoser" class="confirm-card"><view><text>确认预约时间</text><input placeholder="开始：2026-11-21 14:00" @input="onStart"/><input placeholder="结束：2026-11-21 17:00" @input="onEnd"/></view><button @tap="confirmTime">确认</button></view>
  <view class="composer"><button @tap="chooseImage">图片</button><input :value="text" @input="onText" @confirm="send" confirm-type="send" placeholder="仅用于订单咨询与履约沟通"/><button @tap="send">发送</button></view>
</view>

</template>
<script>
import { request } from '../../services/api'
const base = () => getApp().globalData.apiBaseUrl
export default {
data() { return { name: '聊天', id: '', text: '', messages: [], startAt: '', endAt: '', isCoser: false, loading: false } },
onLoad(options) {
    this.setData({ name: decodeURIComponent(options.name || '聊天'), isCoser: uni.getStorageSync('account_user')?.role === 'coser' })
    uni.setNavigationBarTitle({ title: this.name })
    if (options.id) { this.setData({ id: decodeURIComponent(options.id) }); this.load() }
    else if (options.serviceId || options.orderId) {
      request('/v1/messages/conversations', { method: 'POST', data: options.orderId ? { orderId: decodeURIComponent(options.orderId) } : { serviceId: decodeURIComponent(options.serviceId) } })
        .then(({ id }) => { this.setData({ id }); this.load() })
        .catch(err => uni.showModal({ title: '无法开始会话', content: err.message || '请先登录', showCancel: false }))
    } else uni.showModal({ title: '无法开始会话', content: '缺少真实服务或订单信息', showCancel: false })
  },
onShow() { if (this.id) { this.load(); this.connect() } },
onHide() { this.closeSocket() },
onUnload() { this.closeSocket() },
methods: {
closeSocket() { if (this.socket) { this.socket.close(); this.socket = null } },
connect() {
    if (this.socket || !uni.getStorageSync('account_token') || !base()) return
    const url = `${base().replace(/^http/, 'ws')}/v1/messages/socket`
    const socket = uni.connectSocket({ url })
    this.socket = socket
    socket.onOpen(() => socket.send({ data: JSON.stringify({ type: 'auth', token: uni.getStorageSync('account_token') }) }))
    socket.onMessage(({ data }) => { try { const event = JSON.parse(data); if (event.type === 'conversation.updated' && event.conversationId === this.id) this.load() } catch (_) {} })
    socket.onClose(() => { if (this.socket === socket) this.socket = null })
  },
load() {
    if (!this.id || this.loading) return
    this.setData({ loading: true })
    request(`/v1/messages/conversations/${encodeURIComponent(this.id)}/messages`).then(rows => {
      const me = uni.getStorageSync('account_user')?.id
      this.setData({ messages: rows.reverse().map(item => ({ ...item, from: item.senderId === me ? 'me' : 'other', text: item.type === 'image' ? (item.status === 'approved' ? '[图片，点击查看]' : '[图片审核中]') : item.status === 'pending' ? `${item.body}（审核中，仅自己可见）` : item.status === 'rejected' ? `${item.body}（未通过审核）` : item.body })) })
      return request(`/v1/messages/conversations/${encodeURIComponent(this.id)}/read`, { method: 'POST' })
    }).catch(err => uni.showToast({ title: err.message || '加载失败', icon: 'none' })).finally(() => this.setData({ loading: false }))
  },
onText(event) { this.setData({ text: event.detail.value }) },
send() {
    const text = this.text.trim(); if (!text || !this.id) return
    request(`/v1/messages/conversations/${encodeURIComponent(this.id)}/messages`, { method: 'POST', data: { body: text, clientId: `wx-${Date.now()}-${Math.random().toString(36).slice(2)}` } })
      .then(() => { this.setData({ text: '' }); this.load() })
      .catch(err => uni.showToast({ title: err.message || '发送失败', icon: 'none' }))
  },
chooseImage() {
    if (!this.id) return
    uni.chooseMedia({ count: 1, mediaType: ['image'], success: ({ tempFiles }) => {
      uni.uploadFile({ url: `${base()}/v1/messages/conversations/${encodeURIComponent(this.id)}/images`, filePath: tempFiles[0].tempFilePath, name: 'file', header: { Authorization: `Bearer ${uni.getStorageSync('account_token')}` }, success: ({ statusCode, data }) => {
        if (statusCode >= 200 && statusCode < 300) this.load()
        else { let message = '上传失败'; try { message = JSON.parse(data).message || message } catch (_) {} uni.showToast({ title: message, icon: 'none' }) }
      }, fail: () => uni.showToast({ title: '上传失败', icon: 'none' }) })
    } })
  },
viewImage(event) {
    const item = this.messages[event.currentTarget.dataset.index]
    if (item.type !== 'image' || item.status !== 'approved') return
    uni.downloadFile({ url: `${base()}/v1/messages/conversations/${encodeURIComponent(this.id)}/images/${item.id}`, header: { Authorization: `Bearer ${uni.getStorageSync('account_token')}` }, success: ({ statusCode, tempFilePath }) => { if (statusCode === 200) uni.previewImage({ urls: [tempFilePath] }) } })
  },
report(event) {
    const item = this.messages[event.currentTarget.dataset.index]
    if (item.from === 'me') return
    uni.showActionSheet({ itemList: ['站外交易或导流', '骚扰或不当内容', '其他'], success: ({ tapIndex }) => request(`/v1/messages/messages/${item.id}/reports`, { method: 'POST', data: { reason: ['站外交易或导流', '骚扰或不当内容', '其他'][tapIndex] } }).then(() => uni.showToast({ title: '举报已提交' })).catch(err => uni.showToast({ title: err.message, icon: 'none' })) })
  },
onStart(event) { this.setData({ startAt: event.detail.value }) },
onEnd(event) { this.setData({ endAt: event.detail.value }) },
confirmTime() {
    if (!this.startAt || !this.endAt) return uni.showToast({ title: '请选择开始和结束时间', icon: 'none' })
    request(`/v1/messages/conversations/${encodeURIComponent(this.id)}/confirm-time`, { method: 'POST', data: { startAt: this.startAt.replace(' ', 'T') + ':00', endAt: this.endAt.replace(' ', 'T') + ':00' } })
      .then(() => uni.showToast({ title: '时间已确认' })).catch(err => uni.showToast({ title: err.message, icon: 'none' }))
  },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.chat-page { height: 100vh; box-sizing: border-box; display: flex; flex-direction: column; background: #f4f2f7; }
.safety { padding: 16rpx 28rpx; background: #fff8dd; color: #6b5720; font-size: 23rpx; text-align: center; }
.chat-list { flex: 1; min-height: 0; padding: 24rpx; box-sizing: border-box; }
.bubble-row { margin: 16rpx 0; display: flex; }
.bubble-row--me { justify-content: flex-end; }
.bubble-row--system { justify-content: center; }
.bubble { max-width: 72%; box-sizing: border-box; padding: 18rpx 22rpx; overflow-wrap: anywhere; border-radius: 24rpx 24rpx 24rpx 6rpx; background: #fff; color: var(--color-text); font-size: 27rpx; }
.bubble-row--me .bubble { border-radius: 24rpx 24rpx 6rpx 24rpx; background: var(--color-primary); color: #fff; }
.bubble-row--system .bubble { padding: 10rpx 18rpx; border-radius: 999rpx; background: #e8e5ec; color: var(--color-muted); font-size: 22rpx; }
.confirm-card { padding: 18rpx 24rpx; display: flex; align-items: center; justify-content: space-between; gap: 20rpx; border-top: 1rpx solid var(--color-border); background: #fff; }
.confirm-card>view { flex: 1; min-width: 0; }
.confirm-card text { display: block; }
.confirm-card text:first-child { font-weight: 700; }
.confirm-card text:last-child { color: var(--color-muted); font-size: 22rpx; }
.confirm-card button { min-width: 156rpx; min-height: 88rpx; margin: 0; padding: 0 20rpx; border: 0; border-radius: 999rpx; background: var(--color-primary-soft); color: var(--color-primary); font-size: 24rpx; font-weight: 700; }
.confirm-card button[disabled] { background: var(--color-success-soft); color: var(--color-success); }
.composer { padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); display: flex; gap: 12rpx; border-top: 1rpx solid var(--color-border); background: #fff; }
.composer input { flex: 1; min-width: 0; height: 88rpx; box-sizing: border-box; padding: 0 22rpx; border-radius: 24rpx; background: var(--color-soft); font-size: 26rpx; }
.composer button { width: 112rpx; height: 88rpx; margin: 0; padding: 0; display: flex; align-items: center; justify-content: center; border: 0; border-radius: 999rpx; background: var(--color-primary); color: #fff; font-size: 25rpx; line-height: 1; }

</style>
