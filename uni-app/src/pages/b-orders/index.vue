<template>
<view>
  <scroll-view class="filters" scroll-x><button v-for="item in filters" :key="item.key" :class="filter === item.key ? 'active' : ''" :data-key="item.key" @tap="select">{{ item.label }}</button></scroll-view>
  <view class="page">
    <view v-for="item in visibleOrders" :key="item.id" class="section order">
      <view class="section__head"><text class="section__title">{{ item.title }}</text><text class="tag">{{ item.statusText }}</text></view>
      <text class="muted">{{ item.customer }} · {{ item.date }} · ¥{{ item.amount }}</text>
      <text class="muted">{{ item.place }}</text>
      <view class="btn-row">
        <button class="btn btn--small" :data-id="item.id" @tap="contact">私信</button>
        <button v-if="item.status === 'paid_pending_confirm'" class="btn btn--danger btn--small" :data-id="item.id" @tap="reject">无法接单</button>
        <button v-if="item.status === 'paid_pending_confirm'" class="btn btn--primary btn--small" :data-id="item.id" @tap="accept">确认接单</button>
        <button v-if="item.status === 'service'" class="btn btn--primary btn--small" :data-id="item.id" @tap="checkIn">现场签到</button>
        <button v-if="item.status === 'in_service'" class="btn btn--primary btn--small" :data-id="item.id" @tap="complete">确认完成</button>
        <button v-if="item.status === 'service' || item.status === 'in_service'" class="btn btn--danger btn--small" :data-id="item.id" @tap="openException">异常</button>
      </view>
    </view>
    <view v-if="!visibleOrders.length" class="empty">当前状态下没有委托订单</view>
  </view>
  <button class="emergency-float" @tap="callEmergencyContact">紧急联系</button>
  <view v-if="checkInOpen" class="checkin-mask" @tap="closeCheckIn"><view class="checkin-sheet" @tap.stop="stopTap">
    <view class="section__head"><text class="section__title">现场签到</text><button @tap="closeCheckIn">×</button></view>
    <text class="muted">向用户索取4位履约码，定位并拍摄现场照片。</text>
    <input class="checkin-input" type="number" maxlength="4" :value="checkInInput" @input="onCheckInCode" placeholder="4位履约码"/>
    <button class="btn" @tap="captureLocation">{{ checkInLocation ? '已获取位置' : '获取当前位置' }}</button>
    <button class="btn" @tap="chooseCheckInPhoto">{{ checkInPhoto ? '已拍摄照片' : '拍摄现场照片' }}</button>
    <image v-if="checkInPhoto" class="checkin-preview" :src="checkInPhoto" mode="aspectFill"/>
    <button class="btn btn--primary" @tap="submitCheckIn" :loading="checkInBusy" :disabled="checkInBusy">确认开始履约</button>
  </view></view>
  <b-tabbar selected="orders"/>
</view>
</template>
<script>
import BTabbar from '../../components/b-tabbar/index.vue'
import { request } from '../../services/api'
import { normalizeOrder } from '../../utils/order-api'
import { callEmergencyContact } from '../../utils/emergency'
export default {
  components: { BTabbar },
  data() { return { filter: 'all', filters: [{key:'all',label:'全部'},{key:'pending',label:'待确认'},{key:'service',label:'待履约'},{key:'done',label:'已完成'}],
    orders: [], visibleOrders: [], checkInOpen: false, checkInOrderId: '', checkInInput: '', checkInPhoto: '', checkInLocation: null, checkInBusy: false } },
  onLoad(options) { this.filter = ['all','pending','service','done'].includes(options.filter) ? options.filter : 'all' },
  onShow() { this.refresh() },
  methods: {
    setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) },
    refresh() {
      if (!uni.getStorageSync('account_token')) return
      request('/v1/coser/orders').then(rows => this.setData({ orders: rows.map(normalizeOrder) }, this.applyFilter))
        .catch(err => uni.showToast({ title: err.message || '订单加载失败', icon: 'none' }))
    },
    applyFilter() {
      const states = { pending: ['paid_pending_confirm'], service: ['service','in_service'], done: ['completed','cancelled','cancel_pending'] }
      this.visibleOrders = this.filter === 'all' ? this.orders : this.orders.filter(item => states[this.filter].includes(item.status))
    },
    select(event) { this.setData({ filter: event.currentTarget.dataset.key }, this.applyFilter) },
    contact(event) { uni.navigateTo({ url: '/pages/chat/index?orderId=' + encodeURIComponent(event.currentTarget.dataset.id) }) },
    callEmergencyContact,
    action(path, id, data) {
      return request('/v1/orders/' + encodeURIComponent(id) + '/' + path, { method: 'POST', data })
        .then(() => this.refresh()).catch(err => uni.showToast({ title: err.message || '操作失败', icon: 'none' }))
    },
    accept(event) { this.action('confirm',event.currentTarget.dataset.id) },
    reject(event) {
      const id=event.currentTarget.dataset.id
      const reasons=['身体不适无法履约','档期冲突','服装或道具异常','需求超出服务边界','其他原因']
      uni.showActionSheet({ itemList: reasons, success: ({ tapIndex }) => this.action('reject',id,{ reason: reasons[tapIndex] }) })
    },
    complete(event) { this.action('complete-confirmations',event.currentTarget.dataset.id) },
    openException(event) {
      const id=event.currentTarget.dataset.id
      const reasons=['用户未到场','地点存在安全风险','用户需求超出边界','取消责任争议','其他异常']
      uni.showActionSheet({ itemList: reasons, success: ({ tapIndex }) => this.action('exceptions',id,{ reason: reasons[tapIndex] }) })
    },
    checkIn(event) { this.setData({ checkInOpen: true, checkInOrderId: event.currentTarget.dataset.id, checkInInput: '', checkInPhoto: '', checkInLocation: null }) },
    closeCheckIn() { this.checkInOpen = false },
    stopTap() {},
    onCheckInCode(event) { this.checkInInput = event.detail.value.replace(/\D/g, '').slice(0, 4) },
    captureLocation() { uni.getLocation({ type: 'gcj02', success: ({ latitude, longitude }) => { this.checkInLocation = { latitude, longitude } }, fail: () => uni.showToast({ title: '请允许定位后重试', icon: 'none' }) }) },
    chooseCheckInPhoto() { uni.chooseMedia({ count: 1, mediaType: ['image'], sourceType: ['camera'], success: ({ tempFiles }) => { if (tempFiles && tempFiles[0]) this.checkInPhoto = tempFiles[0].tempFilePath } }) },
    submitCheckIn() {
      if (this.checkInBusy) return
      if (!/^\d{4}$/.test(this.checkInInput) || !this.checkInLocation || !this.checkInPhoto)
        return uni.showToast({ title: '请填写履约码、定位并拍照', icon: 'none' })
      this.checkInBusy = true
      uni.uploadFile({ url: getApp().globalData.apiBaseUrl + '/v1/orders/' + encodeURIComponent(this.checkInOrderId) + '/check-in',
        filePath: this.checkInPhoto, name: 'photo',
        formData: { code: this.checkInInput, latitude: String(this.checkInLocation.latitude), longitude: String(this.checkInLocation.longitude) },
        header: { Authorization: 'Bearer ' + uni.getStorageSync('account_token') },
        success: ({ statusCode, data }) => {
          if (statusCode >= 200 && statusCode < 300) { this.checkInOpen = false; this.refresh(); uni.showToast({ title: '签到成功', icon: 'success' }) }
          else { let message='签到失败'; try { message=JSON.parse(data).message || message } catch (_) {} uni.showToast({ title: message, icon: 'none' }) }
        }, fail: () => uni.showToast({ title: '上传失败', icon: 'none' }), complete: () => { this.checkInBusy = false } })
    }
  }
}
</script>
<style>
.filters { padding: 10rpx 28rpx; white-space: nowrap; background: #fff; }
.filters button { display: inline-block; margin-right: 10rpx; padding: 0 22rpx; border: 0; border-radius: 999rpx; background: transparent; color: var(--color-muted); }
.filters .active { background: var(--color-primary-soft); color: var(--color-primary-ink); }
.order { margin-top: 20rpx; }
.order .muted { display: block; margin: 8rpx 0; }
.btn-row { display: flex; flex-wrap: wrap; gap: 10rpx; margin-top: 20rpx; }
.emergency-float { position: fixed; right: 24rpx; top: 220rpx; z-index: 1000; border-radius: 999rpx; background: #c9384f; color: #fff; }
.checkin-mask { position: fixed; z-index: 1100; inset: 0; display: flex; align-items: flex-end; background: rgba(25,20,32,.48); }
.checkin-sheet { width: 100%; padding: 30rpx; border-radius: 32rpx 32rpx 0 0; background: #fff; }
.checkin-sheet .btn { width: 100%; margin: 12rpx 0; }
.checkin-input { height: 96rpx; margin: 16rpx 0; padding: 0 22rpx; border: 1rpx solid var(--color-border); }
.checkin-preview { width: 120rpx; height: 120rpx; }
</style>
