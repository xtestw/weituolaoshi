<template>
<view class="page messages-page">
  <button v-for="(item, index) in messages" :key="item.id" class="message-item" :data-index="index" @tap="open">
    <view :class="&quot;avatar avatar--&quot; + (item.type)"><text>{{ item.avatar }}</text></view>
    <view class="message-copy"><view class="message-copy__head"><text class="message-copy__name">{{ item.name }}</text><text class="message-copy__time">{{ item.time }}</text></view><view class="message-copy__body"><text class="message-copy__preview">{{ item.preview }}</text><text v-if="item.unread" class="unread">{{ item.unread }}</text></view></view>
  </button>
  <view v-if="!loading && !messages.length" class="empty">
    <text class="empty__text">{{ loggedIn ? '暂无消息' : '登录后查看消息' }}</text>
    <button v-if="!loggedIn" class="empty__action" @tap="goLogin">去登录</button>
  </view>
</view>
<b-tabbar selected="messages"/>

</template>
<script>
import BTabbar from '../../components/b-tabbar/index.vue'
import { request } from '../../services/api'
function mapRow(row) {
  return {
    ...row,
    avatar: (row.name || '聊').slice(-1),
    preview: row.preview || '开始聊天',
    unread: Number(row.unread || 0),
    time: '',
    type: row.kind === 'official' ? 'system' : 'person'
  }
}
export default {
components: { BTabbar },
data() { return { messages: [], loading: false, loggedIn: false } },
onShow() {
    this.refresh()
  },
methods: {
refresh() {
    const loggedIn = !!uni.getStorageSync('account_token')
    if (!loggedIn) { this.setData({ loggedIn: false, messages: [], loading: false }); return }
    this.setData({ loggedIn: true, loading: true })
    request('/v1/messages/conversations')
      .then(rows => this.setData({ messages: rows.map(mapRow) }))
      .catch(err => uni.showToast({ title: err.message || '加载失败', icon: 'none' }))
      .finally(() => this.setData({ loading: false }))
  },
goLogin() { uni.switchTab({ url: '/pages/profile/index' }) },
open(event) {
    const item = this.messages[event.currentTarget.dataset.index]
    uni.navigateTo({ url: `/pages/chat/index?id=${encodeURIComponent(item.id)}&name=${encodeURIComponent(item.name || '聊天')}` })
  },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.messages-page { padding-top: 12rpx; padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
.message-item { width: 100%; min-height: 132rpx; margin: 0; padding: 20rpx 0; display: flex; align-items: center; gap: 20rpx; border: 0; border-bottom: 1rpx solid var(--color-border); background: transparent; color: var(--color-text); text-align: left; }
.avatar { width: 88rpx; height: 88rpx; flex: none; display: flex; align-items: center; justify-content: center; border-radius: 28rpx; background: linear-gradient(145deg,#ede9fe,#ddd6fe); color: #4d4595; font-weight: 800; }
.avatar--system { background: var(--color-success-soft); color: var(--color-success); }
.avatar--order { background: var(--color-cta-soft); color: var(--color-cta); }
.message-copy { flex: 1; min-width: 0; }
.message-copy__name { color: var(--color-text); font-size: var(--type-card); line-height: 1.3; font-weight: var(--weight-card); }
.message-copy__time { color: var(--color-muted); font-size: var(--type-caption); }
.message-copy__preview { color: var(--color-muted); font-size: var(--type-meta); line-height: 1.4; }
.message-copy__head,.message-copy__body { display: flex; justify-content: space-between; gap: 14rpx; }
.message-copy__head { align-items: baseline; }
.message-copy__body { align-items: center; margin-top: 8rpx; }
.message-copy__name,.message-copy__preview { flex: 1; min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.message-copy__time { flex: none; }
.unread { min-width: 36rpx; height: 36rpx; padding: 0 8rpx; box-sizing: border-box; border-radius: 999rpx; background: var(--color-cta); color: #fff!important; line-height: 36rpx; text-align: center; }
.empty { padding: 80rpx 24rpx; display: flex; flex-direction: column; align-items: center; gap: 24rpx; }
.empty__text { color: var(--color-muted); font-size: 26rpx; text-align: center; }
.empty__action { margin: 0; padding: 0 36rpx; height: 64rpx; line-height: 64rpx; border-radius: 999rpx; border: 0; background: var(--color-cta); color: #fff; font-size: 26rpx; }

</style>
