<template>
<view :class="&quot;status-hero status-hero--&quot; + (order.status)"><text class="status-hero__title">{{ order.statusText }}</text><text v-if="order.status === 'unpaid'">完成成年与女性身份校验后付款</text><text v-else-if="order.status === 'paid_pending_confirm'">Coser将在2小时内确认，超时进入退款处理</text><text v-else-if="order.status === 'cancel_pending'">退款申请已提交，等待资金退回</text><text v-else>请按订单约定完成后续操作</text></view>
<view class="page detail-page">
  <view v-if="order.status === 'unpaid' && showIdentityCard" :class="&quot;section identity-card &quot; + (identityPassed ? 'identity-card--passed' : '')">
    <view class="section__head"><text class="section__title">首次付款前身份校验</text><text :class="&quot;tag &quot; + (identityPassed ? 'tag--success' : '')">{{ identityPassed ? '已通过' : '待完成' }}</text></view>
    <text class="muted">仅用于校验年满18周岁和女性身份。页面不会向Coser或其他用户展示任何证件信息。</text>
    <view v-if="identityPassed" class="identity-result"><view>✓</view><text>{{ identityText }}</text></view>
    <button v-else-if="!verifiedIdentity" class="btn btn--primary identity-action" @tap="verifyIdentity">去实名认证</button><text v-else class="field-error">身份已核验，当前不符合下单条件；仍可浏览内容。</text>
  </view>
  <view class="section"><view class="service"><view class="cover">MAR</view><view><text class="section__title">{{ order.title }}</text><text class="muted">Coser：{{ order.coser }}</text><text class="price">¥{{ order.amount }}</text></view></view></view>
  <view class="section"><view class="line"><text>时间</text><text class="line__value">{{ order.date }}</text></view><view class="line"><text>地点</text><text class="line__value">{{ order.place }}</text></view><view class="line"><text>相处边界</text><text class="line__value">{{ boundaryText }}</text></view><view class="line"><text>服务金额</text><text class="line__value">¥{{ order.amount }}</text></view><view class="line"><text>用户侧平台服务费</text><text class="line__value">¥0</text></view><view class="line"><text>订单编号</text><text class="line__value">{{ order.id }}</text></view></view>
  <view v-if="order.status === 'service' || order.status === 'in_service'" class="section"><view class="section__head"><text class="section__title">履约签到</text><text :class="&quot;tag &quot; + (order.status === 'in_service' ? 'tag--success' : '')">{{ order.status === 'in_service' ? '已开始' : '待现场确认' }}</text></view><text class="muted">订单确认后自动生成4位码。现场告知Coser，由Coser填码、获取位置并上传现场照片，三项通过后开始履约。</text><text v-if="checkInCode && order.status === 'service'" class="checkin-code">{{ checkInCode }}</text><text v-if="checkInCode && order.status === 'service'" class="caption">本次签到码固定不变，签到后即失效。请仅在现场告知Coser。</text></view>
  <view v-if="order.status === 'completed'" class="section"><view class="section__head"><text class="section__title">给Coser评分</text></view><button class="btn btn--primary" @tap="rateOrder" :disabled="reviewed">{{ reviewed ? '已评分' : '为本次委托打分' }}</button></view>
  <view class="section progress"><view class="section__head"><text class="section__title">委托进度</text></view><view :class="&quot;step &quot; + (order.status !== 'unpaid' ? 'step--done' : '')"><view></view><text>用户付款</text><text>{{ order.status === 'unpaid' ? '待完成' : '平台担保中' }}</text></view><view :class="&quot;step &quot; + (order.status === 'service' || order.status === 'in_service' || order.status === 'completed' ? 'step--done' : '')"><view></view><text>Coser确认</text><text>最长24小时</text></view><view :class="&quot;step &quot; + (order.status === 'in_service' || order.status === 'completed' ? 'step--done' : '')"><view></view><text>现场签到</text><text>C端出码 · B端取证</text></view><view :class="&quot;step &quot; + (order.status === 'completed' ? 'step--done' : '')"><view></view><text>双方确认并结算</text><text>完成后打款</text></view></view>
  <view class="section"><view class="section__head"><text class="section__title">取消与异常</text></view><text class="muted">履约前48小时外可无责取消；48小时内需填写原因并由双方确认责任。发生安全问题请立即终止履约并联系平台。</text><button v-if="['unpaid','paid_pending_confirm','service'].includes(order.status)" class="btn btn--danger cancel" @tap="cancel">申请取消订单</button></view>
  <view class="section safety-card"><view class="section__head"><text class="section__title">安全保障</text><text class="tag tag--success">平台担保</text></view><view class="line"><text>服务场所</text><text class="line__value">仅限公共场所</text></view><view class="line"><text>履约验证</text><text class="line__value">双方签到 · 一次性履约码</text></view><view class="line"><text>资金结算</text><text class="line__value">双方确认完成后打款</text></view></view>
</view>
<button v-if="order.status === 'service' || order.status === 'in_service'" class="emergency-float" @tap="callEmergencyContact" aria-label="紧急联系预设联系人">紧急联系</button>
<view class="bottom-actions"><button class="btn" @tap="contact">联系Coser</button><button v-if="order.status === 'unpaid'" class="btn btn--cta" @tap="pay">{{ identityPassed ? '立即支付 ¥' + order.amount : '核验下单资格' }}</button><button v-else-if="order.status === 'in_service'" class="btn btn--cta" @tap="complete">确认完成</button><button v-else class="btn btn--primary" disabled>平台担保中</button></view>

</template>
<script>
import { get, set } from '../../utils/store'
import { createPayment, request } from '../../services/api'
import { callEmergencyContact } from '../../utils/emergency'
import { saveOrder } from '../../utils/order-api'

const cancelReasons = {
  customer: ['行程计划有变', '身体不适无法到场', '与Coser协商一致', '时间或地点需要调整', '误操作下单', '其他原因'],
  coser: ['身体不适无法履约', '档期冲突', '服装或道具异常', '公共场所临时关闭', '用户需求超出服务边界', '与用户协商一致', '其他原因']
}

export default {
data() { return { order: {}, isNew: false, role: 'customer', showIdentityCard: false, identityPassed: false, identityText: '', checkInCode: '', reviewed: false, boundaryText: '不接受肢体接触 · 轻挽手臂 · 不饮酒' } },
onLoad(options) { this.setData({ isNew: options.new === '1', showIdentityCard: !get('verification', null) }); this.loadOrder(options.id) },
onShow() {
    if (this.order.id) this.loadOrder(this.order.id)
    const verification = get('verification', null)
    const coserGender = this.order.coserGender || 'F'
    const identityPassed = !!(verification && verification.adult && verification.gender === coserGender)
    this.setData({ identityPassed, identityText: identityPassed ? `已成年 · ${verification.gender === 'F' ? '女' : '男'} · 同性校验通过` : '', verifiedIdentity: !!(verification && verification.verified), showIdentityCard: !identityPassed })
    if (uni.getStorageSync('account_token')) request('/v1/identity/me').then(result => {
      const passed = result.status === 'verified' && result.adult
      if (passed) set('verification', { verified: true, adult: true, gender: result.gender, maskedId: result.maskedId })
      this.setData({ identityPassed: passed, verifiedIdentity: result.status === 'verified', showIdentityCard: !passed, identityText: passed ? `已成年 · ${result.gender === 'F' ? '女' : '男'} · 身份核验通过` : '' })
    }).catch(() => {})
  },
methods: {
loadOrder(id) {
    const order = get('orders', []).find((item) => item.id === id) || get('orders', [])[0]
    if (!order) return
    const codes = get('checkInCodes', {})
    let code = codes[order.id]
    if (order.status === 'service' && !code && !order.id.startsWith('ord-')) {
      code = { code: String(Math.floor(1000 + Math.random() * 9000)) }
      set('checkInCodes', { ...codes, [order.id]: code })
    }
    this.setData({ order, checkInCode: order.status === 'service' && code ? code.code : '', reviewed: get('orderReviews', []).some((item) => item.orderId === order.id), boundaryText: (order.boundaries || ['不接受肢体接触', '轻挽手臂', '不饮酒']).join(' · ') })
    if (order.id.startsWith('ord-') && uni.getStorageSync('account_token')) request(`/v1/orders/${encodeURIComponent(order.id)}`).then((latest) => {
      const updated = saveOrder(latest)
      this.setData({ order: updated, checkInCode: updated.checkInCode || '' })
    }).catch(() => {})
  },
verifyIdentity() { uni.navigateTo({ url: '/pages/verify/index?returnTo=order' }) },
pay() {
    if (!this.identityPassed) return this.verifyIdentity()
    if (getApp().globalData.localDevMode && this.order.id.startsWith('ord-'))
      return request(`/v1/dev/orders/${encodeURIComponent(this.order.id)}/pay`, { method: 'POST' })
        .then(raw => this.setData({ order: saveOrder(raw) }))
        .catch(err => uni.showToast({ title: err.message || '付款失败', icon: 'none' }))
    createPayment(this.order.id).catch(err => uni.showModal({ title: '暂无法支付', content: err.message || '支付服务暂不可用', showCancel: false }))
  },
updateStatus(status, statusText) {
    const orders = get('orders', []).map((item) => item.id === this.order.id ? { ...item, status, statusText } : item)
    set('orders', orders)
    this.setData({ order: { ...this.order, status, statusText } })
  },
contact() { uni.navigateTo({ url: `/pages/chat/index?orderId=${encodeURIComponent(this.order.id)}` }) },
callEmergencyContact,
  rateOrder() {
    if (this.order.id.startsWith('ord-')) return uni.showActionSheet({ itemList: ['5分 · 非常满意', '4分 · 满意', '3分 · 一般', '2分 · 不满意', '1分 · 很不满意'], success: ({ tapIndex }) => {
      request(`/v1/orders/${encodeURIComponent(this.order.id)}/reviews`, { method: 'POST', data: { score: 5 - tapIndex } })
        .then(() => this.setData({ reviewed: true })).catch(err => uni.showToast({ title: err.message || '评价失败', icon: 'none' }))
    } })
    if (this.order.status !== 'completed' || this.reviewed) return
    uni.showActionSheet({ itemList: ['5分 · 非常满意', '4分 · 满意', '3分 · 一般', '2分 · 不满意', '1分 · 很不满意'], success: ({ tapIndex }) => {
      const reviews = get('orderReviews', [])
      if (reviews.some((item) => item.orderId === this.order.id)) return
      set('orderReviews', [{ orderId: this.order.id, coserId: 'mine', score: 5 - tapIndex, createdAt: Date.now() }, ...reviews])
      this.setData({ reviewed: true })
    } })
  },
cancel() {
    if (this.order.id && this.order.id.startsWith('ord-')) {
      return uni.showActionSheet({ itemList: cancelReasons.customer, success: ({ tapIndex }) => uni.showModal({ title: '确认申请取消？', content: cancelReasons.customer[tapIndex], confirmColor: '#c9384f', success: async ({ confirm }) => {
        if (!confirm) return
        try {
          const raw = await request(`/v1/orders/${encodeURIComponent(this.order.id)}/cancel-requests`, { method: 'POST', data: { reason: cancelReasons.customer[tapIndex] } })
          this.setData({ order: saveOrder(raw) })
          uni.showToast({ title: '申请已提交', icon: 'success' })
        } catch (err) { uni.showModal({ title: '取消失败', content: err.message || '请稍后重试', showCancel: false }) }
      } }) })
    }
    uni.showActionSheet({ itemList: cancelReasons[this.role], success: ({ tapIndex }) => {
      const reason = cancelReasons[this.role][tapIndex]
      uni.showModal({
        title: '确认申请取消？',
        content: `原因：${reason}\n\n具体退款金额以订单状态、实际支付金额和取消受理时间由平台核算。`,
        confirmText: '提交申请',
        confirmColor: '#c9384f',
        success: ({ confirm }) => {
          if (!confirm) return
          this.updateStatus('cancel_pending', '取消申请处理中')
          uni.showToast({ title: '已提交', icon: 'none' })
        }
      })
    } })
  },
complete() {
    if (this.order.id && this.order.id.startsWith('ord-')) return uni.showModal({ title: '确认履约完成', content: '等待双方确认后订单才会完成。', success: ({ confirm }) => {
      if (!confirm) return
      request(`/v1/orders/${encodeURIComponent(this.order.id)}/complete-confirmations`, { method: 'POST' })
        .then(raw => { const latest = saveOrder(raw); this.setData({ order: latest }); uni.showToast({ title: latest.status === 'completed' ? '订单已完成' : '已确认，等待对方', icon: 'none' }) })
        .catch(err => uni.showToast({ title: err.message || '确认失败', icon: 'none' }))
    } })
    uni.showModal({ title: '确认履约完成', content: '双方确认完成后，平台会将订单款结算给Coser。', success: ({ confirm }) => confirm && this.updateStatus('completed', '已完成') })
  },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.status-hero { padding: 44rpx 34rpx; background: linear-gradient(135deg,#ede9fe,#f3e8ff); }
.status-hero text { display: block; }
.status-hero__title { font-size: var(--type-page); line-height: 1.25; font-weight: var(--weight-page); }
.status-hero text:last-child { margin-top: 8rpx; color: var(--color-muted); font-size: 24rpx; }
.status-hero--completed { background: var(--color-success-soft); }
.status-hero--cancel_pending { background: var(--color-danger-soft); }
.detail-page { padding-top: 4rpx; }
.service { display: flex; gap: 20rpx; align-items: center; }
.cover { width: 132rpx; height: 132rpx; flex: none; padding: 14rpx; box-sizing: border-box; display: flex; align-items: flex-end; border-radius: 20rpx; background: linear-gradient(145deg,#ede9fe,#ddd6fe); color: rgba(36,29,45,.6); font-weight: 800; }
.service>view:last-child { flex: 1; min-width: 0; }
.service text { display: block; }
.service .price { margin-top: 8rpx; }
.line__value { max-width: 66%; overflow-wrap: anywhere; }
.step { position: relative; min-height: 92rpx; padding-left: 50rpx; display: grid; grid-template-columns: minmax(0,1fr) auto; align-items: start; gap: 12rpx; font-size: 26rpx; }
.step::before { content: ''; position: absolute; left: 14rpx; top: 30rpx; bottom: -30rpx; width: 2rpx; background: #ddd8e2; }
.step:last-child::before { display: none; }
.step>view { position: absolute; left: 0; top: 8rpx; width: 28rpx; height: 28rpx; border: 5rpx solid #c4bdca; border-radius: 50%; background: #fff; }
.step>text:last-child { color: var(--color-muted); font-size: 23rpx; }
.step--done>view { border-color: var(--color-primary); background: var(--color-primary); box-shadow: inset 0 0 0 6rpx #fff; }
.cancel { margin-top: 24rpx; }
.identity-card { border-color: #ddd6fe; background: linear-gradient(145deg,#fff,#f5f3ff); }
.identity-card--passed { border-color: #bfe6d6; background: linear-gradient(145deg,#fff,#eef9f4); }
.identity-action { margin-top: 22rpx; }
.identity-result { margin-top: 20rpx; padding: 18rpx; display: flex; align-items: center; gap: 14rpx; border-radius: 18rpx; background: var(--color-success-soft); color: var(--color-success); font-size: 25rpx; font-weight: 700; }
.identity-result view { width: 40rpx; height: 40rpx; border-radius: 50%; background: var(--color-success); color: #fff; line-height: 40rpx; text-align: center; }
.safety-card { margin-bottom: 20rpx; }
.checkin-code { display: block; margin: 18rpx 0; color: var(--color-primary); font-size: 64rpx; font-weight: 800; letter-spacing: 12rpx; text-align: center; font-variant-numeric: tabular-nums; }
.emergency-float { position: fixed; z-index: 999; right: 24rpx; bottom: calc(150rpx + env(safe-area-inset-bottom)); min-width: 176rpx; height: 88rpx; margin: 0; padding: 0 20rpx; display: flex; align-items: center; justify-content: center; border: 2rpx solid #fff; border-radius: 999rpx; background: #c9384f; color: #fff; font-size: 26rpx; font-weight: 800; box-shadow: 0 10rpx 32rpx rgba(114,25,45,.3); }

</style>
