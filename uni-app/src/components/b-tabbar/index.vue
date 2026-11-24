<template>
<view class="b-tabbar" role="tablist"><button v-for="(item, index) in tabs" :key="item.key" :class="&quot;b-tab &quot; + (selected === item.key ? 'b-tab--active' : '')" :data-key="item.key" @tap="navigate" :aria-label="item.label" :aria-selected="selected === item.key"><view :class="&quot;b-tab__mark b-tab__mark--&quot; + (item.key)" aria-hidden="true"></view><text>{{ item.label }}</text></button></view>

</template>
<script>
export default {
props: {selected: { type: String, default: 'dashboard' }},
data() { return {
    tabs: [
      { key: 'dashboard', mark: '台', label: '工作台', path: '/pages/b-dashboard/index' },
      { key: 'orders', mark: '单', label: '订单', path: '/pages/b-orders/index' },
      { key: 'messages', mark: '信', label: '消息', path: '/pages/b-messages/index' },
      { key: 'calendar', mark: '历', label: '档期', path: '/pages/b-calendar/index' },
      { key: 'profile', mark: '我', label: '我的', path: '/pages/b-profile/index' }
    ]
  } },
methods: {
navigate(event) {
      const item = this.tabs.find((tab) => tab.key === event.currentTarget.dataset.key)
      if (!item || item.key === this.selected) return
      uni.redirectTo({ url: item.path })
    },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.b-tabbar { --color-border: #e7e2ec; --color-muted: #625b6b; --color-primary-ink: #4c1d95; --color-primary-soft: #f3e8ff; position: fixed; z-index: 75; left: 0; right: 0; bottom: 0; height: calc(124rpx + env(safe-area-inset-bottom)); box-sizing: border-box; padding: 10rpx 12rpx calc(10rpx + env(safe-area-inset-bottom)); display: grid; grid-template-columns: repeat(5,minmax(0,1fr)); align-items: center; gap: 4rpx; border-top: 1rpx solid var(--color-border); background: #fff; box-shadow: 0 -8rpx 26rpx rgba(55,42,77,.06); }
.b-tab { width: 100%; min-height: 104rpx; margin: 0; padding: 10rpx 0 8rpx; border: 0; border-radius: 24rpx; background: transparent; color: var(--color-muted); font-size: 22rpx; line-height: 1.2; font-weight: 550; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 7rpx; }
.b-tab__mark { position: relative; width: 42rpx; height: 42rpx; box-sizing: border-box; color: inherit; }
.b-tab__mark--dashboard { border: 4rpx solid currentColor; border-radius: 9rpx; }
.b-tab__mark--dashboard::before,.b-tab__mark--dashboard::after { content: ''; position: absolute; background: currentColor; }
.b-tab__mark--dashboard::before { left: 15rpx; top: 0; bottom: 0; width: 4rpx; }
.b-tab__mark--dashboard::after { left: 0; right: 0; top: 15rpx; height: 4rpx; }
.b-tab__mark--orders { width: 36rpx; border: 4rpx solid currentColor; border-radius: 7rpx; }
.b-tab__mark--orders::before,.b-tab__mark--orders::after { content: ''; position: absolute; left: 7rpx; right: 7rpx; height: 4rpx; border-radius: 4rpx; background: currentColor; }
.b-tab__mark--orders::before { top: 11rpx; }
.b-tab__mark--orders::after { top: 23rpx; }
.b-tab__mark--messages { border: 4rpx solid currentColor; border-radius: 12rpx 12rpx 12rpx 4rpx; }
.b-tab__mark--messages::before,.b-tab__mark--messages::after { content: ''; position: absolute; height: 4rpx; border-radius: 4rpx; background: currentColor; }
.b-tab__mark--messages::before { left: 8rpx; right: 8rpx; top: 12rpx; }
.b-tab__mark--messages::after { left: 8rpx; right: 14rpx; top: 22rpx; }
.b-tab__mark--calendar { border: 4rpx solid currentColor; border-radius: 9rpx; }
.b-tab__mark--calendar::before { content: ''; position: absolute; left: 0; right: 0; top: 10rpx; border-top: 4rpx solid currentColor; }
.b-tab__mark--calendar::after { content: ''; position: absolute; left: 10rpx; top: 23rpx; width: 5rpx; height: 5rpx; border-radius: 50%; background: currentColor; box-shadow: 11rpx 0 currentColor; }
.b-tab__mark--profile::before { content: ''; position: absolute; left: 13rpx; top: 0; width: 16rpx; height: 16rpx; box-sizing: border-box; border: 4rpx solid currentColor; border-radius: 50%; }
.b-tab__mark--profile::after { content: ''; position: absolute; left: 4rpx; bottom: 0; width: 34rpx; height: 19rpx; box-sizing: border-box; border: 4rpx solid currentColor; border-bottom: 0; border-radius: 20rpx 20rpx 0 0; }
.b-tab--active { background: var(--color-primary-soft); color: var(--color-primary-ink); font-weight: 750; }
.b-tab--active .b-tab__mark { color: var(--color-primary-ink); }

</style>
