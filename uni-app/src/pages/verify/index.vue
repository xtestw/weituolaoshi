<template>
<view class="page verify-page">
  <view class="verify-hero"><view class="shield"><view></view></view><text class="verify-hero__title">{{ mode === 'coser' ? '完成真人认证' : '确认是你本人' }}</text><text class="muted">{{ mode === 'coser' ? '发布前需核验女性身份与成年状态，核验通过后立即生效，无需人工审核。' : '用于成年与女性身份下单校验，其他用户不会看到任何证件信息。' }}</text></view>
  <view class="section">
    <label class="field-label">真实姓名</label><input class="field" data-field="name" :value="name" @input="onField" placeholder="与身份证保持一致"/><text v-if="errors.name" class="field-error">{{ errors.name }}</text>
    <label class="field-label">身份证号码</label><input class="field" data-field="idNumber" :value="idNumber" @input="onField" type="idcard" maxlength="18" placeholder="仅用于平台安全核验"/><text v-if="errors.idNumber" class="field-error">{{ errors.idNumber }}</text>
    <checkbox-group @change="onAgree"><label class="agreement"><checkbox value="agree" color="#6d28d9"/><text>我已阅读并同意《身份信息处理规则》和《用户协议》</text></label></checkbox-group>
    <text v-if="errors.agreed" class="field-error">{{ errors.agreed }}</text>
  </view>
  <view class="notice"><text>身份信息仅提交服务端核验，前端不保存完整证件号。未配置合规实名服务前，认证暂不可用。</text></view>
  <text v-if="errors.submit" class="field-error">{{ errors.submit }}</text>
  <button class="btn btn--primary submit" @tap="submit" :loading="submitting" :disabled="submitting">{{ submitting ? '正在核验' : '同意授权并完成认证' }}</button>
</view>

</template>
<script>
import { set } from '../../utils/store'
import { verifyIdentity } from '../../services/api'

function parseId(id) {
  const birth = id.slice(6, 14)
  const year = Number(birth.slice(0, 4))
  const month = Number(birth.slice(4, 6))
  const day = Number(birth.slice(6, 8))
  const now = new Date()
  let age = now.getFullYear() - year
  if (now.getMonth() + 1 < month || (now.getMonth() + 1 === month && now.getDate() < day)) age -= 1
  return { adult: age >= 18, age, gender: Number(id[16]) % 2 === 1 ? 'M' : 'F' }
}

export default {
data() { return { name: '', idNumber: '', agreed: false, returnTo: '', mode: 'customer', errors: {}, submitting: false } },
onLoad(options) {
    const mode = options.mode || 'customer'
    this.setData({ returnTo: options.returnTo || '', mode })
    if (mode === 'coser') uni.setNavigationBarTitle({ title: 'Coser真人认证' })
  },
methods: {
onField(event) { this.setData({ [event.currentTarget.dataset.field]: event.detail.value }) },
onAgree(event) { this.setData({ agreed: event.detail.value.length > 0 }) },
async submit() {
    if (this.submitting) return
    const name = this.name.trim()
    const id = this.idNumber.trim().toUpperCase()
    const errors = {}
    if (name.length < 2) errors.name = '请输入身份证上的真实姓名'
    if (!/^\d{17}[\dX]$/.test(id)) errors.idNumber = '请输入有效的18位身份证号码'
    if (!this.agreed) errors.agreed = '请阅读并同意身份核验授权'
    this.setData({ errors })
    if (Object.keys(errors).length) return
    const result = parseId(id)
    if (!result.adult) return this.setData({ errors: { submit: '未满18周岁可继续浏览，暂不能下单或发布服务。' } })
    if (!uni.getStorageSync('account_token'))
      return this.setData({ errors: { submit: '请先在“我的”页面完成微信登录。' } })
    this.setData({ submitting: true })
    try {
      const identity = await verifyIdentity({ name, idNumber: id, mode: this.mode, agreed: true })
      set('verification', { verified: identity.status === 'verified', adult: identity.adult === true, gender: identity.gender, genderEligible: identity.genderEligible === true })
      if (this.mode === 'coser') set('coserVerification', { status: identity.status, reason: identity.reason || '', submittedAt: Date.now() })
      this.setData({ idNumber: '' })
      uni.showToast({ title: identity.faceBypassed ? '测试认证完成' : '认证完成', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 500)
    } catch (error) {
      this.setData({ errors: { submit: error.message || '核验失败，请稍后重试' } })
    } finally { this.setData({ submitting: false }) }
  },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.verify-page { padding-top: 48rpx; }
.verify-hero { padding: 30rpx 24rpx; text-align: center; }
.verify-hero text { display: block; }
.verify-hero__title { margin: 24rpx 0 10rpx; font-size: var(--type-page); line-height: 1.25; font-weight: var(--weight-page); }
.shield { width: 108rpx; height: 118rpx; margin: auto; display: flex; align-items: center; justify-content: center; border-radius: 44rpx 44rpx 54rpx 54rpx; background: linear-gradient(145deg,var(--color-primary),#a78bfa); clip-path: polygon(50% 0,95% 18%,88% 72%,50% 100%,12% 72%,5% 18%); }
.shield view { width: 28rpx; height: 16rpx; border-left: 7rpx solid #fff; border-bottom: 7rpx solid #fff; transform: rotate(-45deg); }
.agreement { min-height: 88rpx; margin-top: 20rpx; display: flex; align-items: center; gap: 12rpx; color: var(--color-muted); font-size: 24rpx; }
.submit { margin-top: 32rpx; }

</style>
