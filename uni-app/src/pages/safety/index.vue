<template>
<view class="page safety-page">
  <view class="section"><view class="section__head"><text class="section__title">实名认证</text><text :class="&quot;tag &quot; + (verified ? 'tag--success' : '')">{{ verified ? '已完成' : '未认证' }}</text></view><text class="caption">首次履约前需要完成实名认证，未满18周岁不可下单。</text><button v-if="!verified" class="btn btn--small verify-button" @tap="openVerification">去认证</button></view>
  <view class="section"><view class="section__head"><text class="section__title">紧急联系人</text><text v-if="saved" class="tag tag--success">已设置</text></view><text class="caption">请填写你信任的人。遇到履约安全问题时，平台可按安全流程协助联系。</text>
    <text class="field-label">姓名</text><input class="field" :value="name" @input="editName" maxlength="20" placeholder="请输入联系人姓名" aria-label="紧急联系人姓名"/>
    <text class="field-label">关系</text><picker class="relation-picker" :range="relationOptions" @change="chooseRelation"><view class="field relation-button"><text>{{ relation }}</text><view class="control-chevron control-chevron--down" aria-hidden="true"></view></view></picker>
    <text class="field-label">手机号</text><input class="field" type="number" :value="phone" @input="editPhone" maxlength="11" placeholder="请输入联系人手机号" aria-label="紧急联系人手机号"/>
    <text v-if="error" class="field-error">{{ error }}</text><button class="btn btn--primary save-button" @tap="saveContact">{{ saved ? '保存修改' : '保存联系人' }}</button>
  </view>
  <view class="notice">当前为本地演示版，联系人仅保存在本机小程序缓存中。正式上线前需接入服务端与安全工单流程。</view>
</view>

</template>
<script>
import { get, set } from '../../utils/store'

export default {
data() { return { verified: false, name: '', phone: '', relation: '家人', relationOptions: ['家人', '朋友', '其他'], error: '', saved: false } },
onShow() {
    const contact = get('emergencyContact', null)
    this.setData({ verified: !!get('verification', null), name: contact ? contact.name : '', phone: contact ? contact.phone : '', relation: contact ? contact.relation : '家人', saved: !!contact, error: '' })
  },
methods: {
editName(event) { this.setData({ name: event.detail.value, error: '' }) },
editPhone(event) { this.setData({ phone: event.detail.value, error: '' }) },
chooseRelation(event) { this.setData({ relation: this.relationOptions[Number(event.detail.value)] }) },
saveContact() {
    const name = this.name.trim()
    const phone = this.phone.trim()
    if (!name) return this.setData({ error: '请填写紧急联系人姓名' })
    if (!/^1[3-9]\d{9}$/.test(phone)) return this.setData({ error: '请填写有效的中国大陆手机号' })
    set('emergencyContact', { name, phone, relation: this.relation })
    this.setData({ name, phone, saved: true, error: '' })
    uni.showToast({ title: '已保存紧急联系人', icon: 'success' })
  },
openVerification() { uni.navigateTo({ url: '/pages/verify/index' }) },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.safety-page { padding-top: 4rpx; }
.verify-button { width: fit-content; margin: 20rpx 0 0; color: var(--color-primary); }
.relation-picker { display: block; width: 100%; }
.relation-button { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.save-button { width: 100%; margin: 30rpx 0 0; }

</style>
