<template>
<scroll-view class="order-tabs" scroll-x show-scrollbar="false"><button v-for="(item, index) in tabs" :key="item.key" :class="tab === item.key ? 'active' : ''" :data-key="item.key" @tap="select">{{ item.label }}</button></scroll-view>
<view class="page orders-page">
  <button v-for="(item, index) in visibleOrders" :key="item.id" class="order-card" :data-id="item.id" @tap="open">
    <view class="order-card__head"><text>{{ item.title }}</text><text>{{ item.statusText }}</text></view>
    <view class="order-card__body"><view class="order-cover">MAR</view><view><text>{{ item.coser }} · {{ item.date }}</text><text>{{ item.place }}</text><text class="price">实付 ¥{{ item.amount }}</text></view></view>
    <view class="order-card__foot"><text>订单号 {{ item.id }}</text><text>查看详情 ›</text></view>
  </button>
  <view v-if="!visibleOrders.length" class="empty"><text>当前状态下还没有委托</text></view>
</view>

</template>
<script>
import { get } from '../../utils/store'
import { loadOrders } from '../../utils/order-api'
export default {
data() { return { tab: 'all', tabs: [{key:'all',label:'全部'},{key:'confirm',label:'待确认'},{key:'pay',label:'待支付'},{key:'service',label:'待履约'},{key:'review',label:'待评价'}], orders: [], visibleOrders: [] } },
onLoad(options) {
  const tab = options?.tab
  if (['all', 'confirm', 'pay', 'service', 'review'].includes(tab)) this.tab = tab
},
onShow() {
  this.setData({ orders: get('orders', []) }, this.applyFilter)
  if (uni.getStorageSync('account_token')) loadOrders().then(orders => this.setData({ orders }, this.applyFilter))
    .catch(err => uni.showToast({ title: err.message || '订单加载失败', icon: 'none' }))
},
methods: {
select(event) { this.setData({ tab: event.currentTarget.dataset.key }, this.applyFilter) },
applyFilter() {
    const statusMap = { confirm: ['paid_pending_confirm'], pay: ['unpaid'], service: ['service'], review: ['completed'] }
    const visibleOrders = this.tab === 'all' ? this.orders : this.orders.filter((item) => (statusMap[this.tab] || []).includes(item.status))
    this.setData({ visibleOrders })
  },
open(event) { uni.navigateTo({ url: `/pages/order-detail/index?id=${event.currentTarget.dataset.id}` }) },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.order-tabs { box-sizing: border-box; width: 100%; padding: 10rpx 28rpx; white-space: nowrap; background: #fff; }
.order-tabs button { min-height: 76rpx; margin-right: 10rpx; padding: 0 22rpx; display: inline-flex; align-items: center; justify-content: center; vertical-align: top; border: 0; border-radius: 999rpx; background: transparent; color: var(--color-muted); font-size: 26rpx; line-height: 1; }
.order-tabs .active { background: var(--color-primary-soft); color: var(--color-primary-ink); font-weight: 750; }
.orders-page { padding-top: 4rpx; }
.order-card { width: 100%; margin: 20rpx 0 0; padding: 26rpx; border: 1rpx solid var(--color-border); border-radius: 28rpx; background: #fff; color: var(--color-text); text-align: left; box-shadow: 0 8rpx 24rpx rgba(55,42,77,.05); }
.order-card__head,.order-card__foot { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.order-card__head text:first-child { font-weight: 750; }
.order-card__head text:last-child { color: var(--color-cta); font-size: 23rpx; }
.order-card__body { margin: 22rpx 0; display: flex; gap: 18rpx; }
.order-cover { width: 128rpx; height: 128rpx; flex: none; padding: 12rpx; box-sizing: border-box; display: flex; align-items: flex-end; border-radius: 20rpx; background: linear-gradient(145deg,#ede9fe,#e9d5ff); color: rgba(36,29,45,.62); font-size: 34rpx; font-weight: 800; }
.order-card__body>view:last-child { flex: 1; min-width: 0; }
.order-card__body text { margin-bottom: 6rpx; display: block; color: var(--color-muted); font-size: 24rpx; }
.order-card__body .price { margin-top: 8rpx; color: var(--color-cta); font-size: 27rpx; }
.order-card__foot { padding-top: 18rpx; border-top: 1rpx solid var(--color-border); color: var(--color-muted); font-size: 22rpx; }
.order-card__foot text:last-child { color: var(--color-primary); font-size: 24rpx; }

</style>
