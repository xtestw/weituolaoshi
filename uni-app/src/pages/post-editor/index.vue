<template>
<view class="editor-shell">
  <view class="editor-nav" :style="&quot;padding-top: &quot; + (navTop) + &quot;px&quot;">
    <button class="editor-nav__close" @tap="closeEditor" aria-label="关闭发帖">×</button>
    <text class="editor-nav__title">发帖</text>
    <button :class="&quot;editor-nav__publish &quot; + (saving ? 'editor-nav__publish--disabled' : '')" @tap="publish" :loading="saving" :disabled="saving">发布</button>
  </view>

  <view class="composer-page">
    <view class="compose-card">
      <view class="media-head">
        <text class="media-head__title">照片</text>
        <text class="media-head__hint">{{ images.length }}/9 · 点击图片设为封面</text>
      </view>
      <view class="media-grid">
        <view v-for="(item, index) in images" :key="item.id" :class="&quot;photo &quot; + (item.cover ? 'photo--cover' : '')" :data-index="index" @tap="setCover">
          <image class="photo__image" :src="item.path" mode="aspectFill"/>
          <text v-if="item.cover" class="photo__cover">封面</text>
          <button class="photo__delete" :data-index="index" @tap.stop="removeImage" :aria-label="&quot;删除第&quot; + (index + 1) + &quot;张图片&quot;">×</button>
        </view>
        <button v-if="images.length < 9" class="media-add" @tap="addImage" aria-label="添加照片">
          <view class="media-add__icon" aria-hidden="true"></view>
          <text class="media-add__title">添加照片</text>
          <text class="media-add__hint">首图更吸引人</text>
        </button>
      </view>

      <input class="compose-title" data-field="title" :value="title" @input="onField" maxlength="30" placeholder="填写角色和服务标题" aria-label="帖子标题"/>
      <textarea class="compose-desc" data-field="description" :value="description" @input="onField" maxlength="1000" placeholder="介绍角色、委托内容、互动方式和适合的场景…" aria-label="帖子正文"/>
      <text class="compose-count">{{ description.length }}/1000</text>
    </view>

    <view class="settings-card">
      <view class="price-row">
        <view class="price-field">
          <text class="price-field__label">小时价格</text>
          <view class="price-field__input"><text class="price-field__currency">¥</text><input class="price-field__control" data-field="price" :value="price" @input="onField" type="digit" placeholder="0" aria-label="每小时价格"/><text class="price-field__unit">元/小时</text></view>
        </view>
        <view class="price-field price-field--hours">
          <text class="price-field__label">起约</text>
          <picker :range="minHourOptions" @change="onMinHours"><view class="price-field__picker"><text>{{ minHours }}小时</text><view class="control-chevron control-chevron--down" aria-hidden="true"></view></view></picker>
        </view>
      </view>
      <view class="price-breakdown" aria-label="小时价格与预计实收明细">
        <view v-if="priceBreakdown" class="price-breakdown__rows">
          <view class="price-breakdown__line"><text>你设置的小时价格</text><text class="price-breakdown__amount">¥{{ priceBreakdown.gross }}/小时</text></view>
          <view class="price-breakdown__line"><text>平台安全保障费用（5%）</text><text class="price-breakdown__amount">-¥{{ priceBreakdown.fee }}/小时</text></view>
          <view class="price-breakdown__line price-breakdown__line--net"><text>Coser预计实收</text><text class="price-breakdown__amount price-breakdown__amount--net">¥{{ priceBreakdown.net }}/小时</text></view>
        </view>
        <text v-else class="price-breakdown__empty">填写小时价格后，查看平台费用和预计实收</text>
        <text class="price-breakdown__hint">实际费用按订单成交金额计算，以结算明细为准。</text>
      </view>

      <button class="setting-row" @tap="choosePlace">
        <view class="setting-row__copy"><text class="setting-row__title">服务地点（选填）</text><text class="setting-row__value">{{ place ? city + ' · ' + place : '暂不填写' }}</text></view><view class="control-chevron" aria-hidden="true"></view>
      </button>
      <button class="setting-row" @tap="openBoundaryEditor">
        <view class="setting-row__copy"><text class="setting-row__title">相处偏好与边界</text><text class="setting-row__value">{{ boundarySummary }}</text></view><view class="control-chevron" aria-hidden="true"></view>
      </button>
      <button class="setting-row" @tap="editQuestions">
        <view class="setting-row__copy"><text class="setting-row__title">委托前确认</text><text class="setting-row__value">已设置 {{ questionCount }} 个问题</text></view><view class="control-chevron" aria-hidden="true"></view>
      </button>
      <button class="setting-row" @tap="openSchedule">
        <view class="setting-row__copy"><text class="setting-row__title">可约档期</text><text class="setting-row__value">沿用账号档期，点击去调整</text></view><view class="control-chevron" aria-hidden="true"></view>
      </button>
    </view>

    <text class="compose-tip">发布后可以继续编辑；帖子进入委托流程后，内容、价格和删除操作将暂时锁定。</text>
    <button class="draft-button" @tap="saveDraft" :disabled="saving">保存草稿</button>
  </view>

  <view v-if="boundarySheetOpen" class="sheet-mask" @tap="closeBoundaryEditor">
    <view class="boundary-sheet" @tap.stop="stopTap">
      <view class="sheet-handle"></view>
      <view class="sheet-head"><view><text class="sheet-head__title">相处偏好与边界</text><text class="sheet-head__hint">用户将在详情页和下单页看到</text></view><button class="sheet-head__close" @tap="closeBoundaryEditor" aria-label="关闭边界编辑">×</button></view>
      <view class="boundary-list">
        <view v-for="(item, index) in boundaries" :key="item.id" class="boundary-item">
          <text class="boundary-item__index">{{ index + 1 }}</text>
          <input class="boundary-item__input" :value="item.text" :data-index="index" @input="editBoundary" maxlength="30" placeholder="填写一条明确边界" :aria-label="&quot;第&quot; + (index + 1) + &quot;条边界&quot;"/>
          <button class="boundary-item__delete" :data-index="index" @tap="removeBoundary" :aria-label="&quot;删除第&quot; + (index + 1) + &quot;条边界&quot;">删除</button>
        </view>
      </view>
      <button class="boundary-add" @tap="addBoundary">＋ 添加一条</button>
      <button class="boundary-confirm" @tap="confirmBoundaries">完成</button>
    </view>
  </view>

  <view v-if="questionSheetOpen" class="sheet-mask" @tap="closeQuestionEditor">
    <view class="boundary-sheet" @tap.stop="stopTap">
      <view class="sheet-handle"></view>
      <view class="sheet-head"><view><text class="sheet-head__title">委托前确认</text><text class="sheet-head__hint">用户下单时会看到这些问题</text></view><button class="sheet-head__close" @tap="closeQuestionEditor" aria-label="关闭问题编辑">×</button></view>
      <view class="boundary-list">
        <view v-for="(item, index) in questions" :key="item.id" class="boundary-item">
          <text class="boundary-item__index">{{ index + 1 }}</text>
          <input class="boundary-item__input" :value="item.text" :data-index="index" @input="editQuestion" maxlength="60" placeholder="填写委托前需要确认的问题" :aria-label="&quot;第&quot; + (index + 1) + &quot;个问题&quot;"/>
          <button class="boundary-item__delete" :data-index="index" @tap="removeQuestion" :aria-label="&quot;删除第&quot; + (index + 1) + &quot;个问题&quot;">删除</button>
        </view>
      </view>
      <button class="boundary-add" @tap="addQuestion">＋ 添加问题</button>
      <button class="boundary-confirm" @tap="confirmQuestions">完成</button>
    </view>
  </view>
</view>

</template>
<script>
import { get, set } from '../../utils/store'
import { validateVenue } from '../../utils/cities'
import { guardBooking } from '../../utils/eligibility'
import { hourlyBreakdown } from '../../utils/pricing'
import { request, uploadServiceImage } from '../../services/api'

const defaultBoundaries = [
  { id: 'b1', text: '不接受肢体接触' },
  { id: 'b2', text: '轻挽手臂' },
  { id: 'b3', text: '不饮酒' }
]
const defaultQuestions = [
  { id: 'q1', text: '怎么称呼你？' },
  { id: 'q2', text: '你喜欢怎样的互动或聊天方式？' },
  { id: 'q3', text: '有什么不喜欢或需要避开的内容？' },
  { id: 'q4', text: '你能接受的肢体接触边界是什么？' }
]
const countQuestions = (questions) => questions.filter((item) => item.text.trim()).length

export default {
data() { return {
    navTop: 66,
    editingId: '',
    originalMediaIds: [],
    images: [],
    title: '',
    description: '',
    price: '',
    priceBreakdown: null,
    minHours: 2,
    minHourOptions: [2, 3, 4, 5, 6],
    city: '上海',
    district: '',
    placeDetails: null,
    place: '',
    boundaries: defaultBoundaries,
    boundarySummary: '不接受肢体接触 · 轻挽手臂 · 不饮酒',
    boundarySheetOpen: false,
    questions: defaultQuestions,
    questionSheetOpen: false,
    questionCount: 4,
    saving: false,
    dirty: false
  } },
onLoad(options) {
    const menu = uni.getMenuButtonBoundingClientRect ? uni.getMenuButtonBoundingClientRect() : null
    // 导航行紧接胶囊下方，保留少量间距即可。
    if (menu && menu.bottom) this.setData({ navTop: menu.bottom + 6 })
    const coserCity = get('coserCity', '')
    if (coserCity) this.setData({ city: coserCity })
    const draft = options.id ? get('draftPost', null) : null
    if (!draft) return
    const blockImages = (draft.blocks || []).filter((item) => item.type === 'image' && item.path)
    const blockText = (draft.blocks || []).find((item) => item.type === 'text')
    const savedCover = draft.coverUrl ? [{ id: 'saved-cover', path: `${getApp().globalData.apiBaseUrl}${draft.coverUrl}`, remoteUrl: draft.coverUrl, cover: true }] : []
    const images = (draft.images && draft.images.length ? draft.images : blockImages.length ? blockImages : savedCover).map((item, index) => ({ ...item, path: item.path || (item.url ? `${getApp().globalData.apiBaseUrl}${item.url}` : ''), remoteUrl: item.remoteUrl || item.url, id: item.id || `image-${index}`, cover: item.cover || index === 0 }))
    const boundaries = draft.boundaries && draft.boundaries.length ? draft.boundaries.map((item,index) => typeof item === 'string' ? { id: `b-${index}`, text: item } : item) : defaultBoundaries
    const questions = Array.isArray(draft.questions) ? draft.questions.map((item,index) => typeof item === 'string' ? { id: `q-${index}`, text: item } : item) : defaultQuestions
    this.setData({
      editingId: draft.id,
      originalMediaIds: (draft.images || []).map(item => item.id),
      images,
      title: draft.title || '',
      description: draft.description || (blockText ? blockText.text : ''),
      price: String(draft.price || Number(draft.pricePerHour) / 100 || ''),
      priceBreakdown: hourlyBreakdown(draft.price || Number(draft.pricePerHour) / 100 || ''),
      minHours: Number(draft.minHours || 2),
      city: coserCity || draft.city || '上海',
      district: draft.district || '',
      placeDetails: draft.placeDetails || null,
      place: draft.place || '',
      boundaries,
      boundarySummary: this.makeBoundarySummary(boundaries),
      questions,
      questionCount: countQuestions(questions)
    })
  },
methods: {
onField(event) {
    const field = event.currentTarget.dataset.field
    const value = event.detail.value
    this.setData({ [field]: value, ...(field === 'price' ? { priceBreakdown: hourlyBreakdown(value) } : {}), dirty: true })
  },
onMinHours(event) {
    this.setData({ minHours: this.minHourOptions[event.detail.value], dirty: true })
  },
addImage() {
    const remaining = 9 - this.images.length
    if (remaining <= 0) return uni.showToast({ title: '最多上传9张照片', icon: 'none' })
    uni.chooseMedia({
      count: remaining,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: ({ tempFiles }) => {
        const additions = tempFiles.map((file, index) => ({
          id: `image-${Date.now()}-${index}`,
          path: file.tempFilePath,
          cover: this.images.length === 0 && index === 0
        }))
        this.setData({ images: this.images.concat(additions), dirty: true })
      }
    })
  },
setCover(event) {
    const selected = Number(event.currentTarget.dataset.index)
    this.setData({ images: this.images.map((item, index) => ({ ...item, cover: index === selected })), dirty: true })
    uni.showToast({ title: '已设为封面', icon: 'none' })
  },
removeImage(event) {
    const selected = Number(event.currentTarget.dataset.index)
    const images = this.images.filter((_, index) => index !== selected)
    if (images.length && !images.some((item) => item.cover)) images[0] = { ...images[0], cover: true }
    this.setData({ images, dirty: true })
  },
choosePlace() {
    if (!get('coserCity', '')) return uni.showToast({ title: '请先选择服务城市', icon: 'none' })
    uni.chooseLocation({ success: (location) => {
      const result = validateVenue(location, this.city)
      if (!result.valid) return uni.showToast({ title: result.reason, icon: 'none' })
      this.setData({ place: result.place, district: result.district, placeDetails: result, dirty: true })
    }, fail: () => uni.showToast({ title: '请在地图中选择公共场所', icon: 'none' }) })
  },
openBoundaryEditor() { this.setData({ boundarySheetOpen: true }) },
closeBoundaryEditor() { this.setData({ boundarySheetOpen: false }) },
stopTap() {},
addBoundary() {
    if (this.boundaries.length >= 8) return uni.showToast({ title: '最多设置8条', icon: 'none' })
    this.setData({ boundaries: this.boundaries.concat({ id: `b-${Date.now()}`, text: '' }), dirty: true })
  },
editBoundary(event) {
    this.setData({ [`boundaries[${event.currentTarget.dataset.index}].text`]: event.detail.value, dirty: true })
  },
removeBoundary(event) {
    if (this.boundaries.length <= 1) return uni.showToast({ title: '至少保留一条边界', icon: 'none' })
    this.setData({ boundaries: this.boundaries.filter((_, index) => index !== Number(event.currentTarget.dataset.index)), dirty: true })
  },
confirmBoundaries() {
    const boundaries = this.boundaries.filter((item) => item.text.trim())
    if (!boundaries.length) return uni.showToast({ title: '请至少填写一条边界', icon: 'none' })
    this.setData({ boundaries, boundarySummary: this.makeBoundarySummary(boundaries), boundarySheetOpen: false, dirty: true })
  },
makeBoundarySummary(boundaries) {
    const labels = boundaries.filter((item) => item.text.trim()).map((item) => item.text.trim())
    return labels.slice(0, 3).join(' · ') + (labels.length > 3 ? ` · 共${labels.length}条` : '')
  },
editQuestions() { this.setData({ questionSheetOpen: true }) },
closeQuestionEditor() { this.setData({ questionSheetOpen: false }) },
addQuestion() {
    if (this.questions.length >= 8) return uni.showToast({ title: '最多设置8个问题', icon: 'none' })
    const questions = this.questions.concat({ id: `q-${Date.now()}`, text: '' })
    this.setData({ questions, questionCount: countQuestions(questions), dirty: true })
  },
editQuestion(event) {
    const index = Number(event.currentTarget.dataset.index)
    const questions = this.questions.map((item, itemIndex) => itemIndex === index ? { ...item, text: event.detail.value } : item)
    this.setData({ questions, questionCount: countQuestions(questions), dirty: true })
  },
removeQuestion(event) {
    const index = Number(event.currentTarget.dataset.index)
    const questions = this.questions.filter((_, itemIndex) => itemIndex !== index)
    this.setData({ questions, questionCount: countQuestions(questions), dirty: true })
  },
confirmQuestions() {
    const questions = this.questions.filter((item) => item.text.trim()).map((item) => ({ ...item, text: item.text.trim() }))
    this.setData({ questions, questionCount: countQuestions(questions), questionSheetOpen: false, dirty: true })
  },
openSchedule() { uni.navigateTo({ url: '/pages/b-calendar/index' }) },
closeEditor() {
    if (!this.dirty) return uni.navigateBack()
    uni.showActionSheet({
      itemList: ['保存草稿并退出', '不保存直接退出'],
      success: ({ tapIndex }) => tapIndex === 0 ? this.persist('draft', '已保存草稿') : uni.navigateBack()
    })
  },
saveDraft() { this.persist('draft', '已保存草稿') },
publish() {
    if (!guardBooking()) return
    if (get('coserVerification', {}).status !== 'verified') return uni.showModal({ title: '请先完成Coser认证', content: '认证通过后才能发布服务帖子。', showCancel: false })
    if (!this.images.length) return uni.showToast({ title: '请至少添加一张照片', icon: 'none' })
    if (!this.title.trim()) return uni.showToast({ title: '请填写帖子标题', icon: 'none' })
    if (!this.description.trim()) return uni.showToast({ title: '请填写帖子正文', icon: 'none' })
    if (!hourlyBreakdown(this.price)) return uni.showToast({ title: '请填写有效的小时价格，最多两位小数', icon: 'none' })
    this.persist('published', '发布成功')
  },
async persist(status, message) {
    this.setData({ saving: true })
    try {
      if (!uni.getStorageSync('account_token')) throw new Error('请先登录')
      const payload = { title: this.title.trim(), description: this.description.trim(), city: this.city, place: this.place, pricePerHour: Math.round(Number(this.price) * 100), minHours: Number(this.minHours), boundaries: this.boundaries.filter(item => item.text.trim()).map(item => item.text.trim()), questions: this.questions.filter(item => item.text.trim()).map(item => item.text.trim()) }
      if (!payload.title || !payload.description || !payload.pricePerHour) throw new Error('请填写标题、正文和价格')
      const service = await request(this.editingId ? `/v1/services/${this.editingId}` : '/v1/services', { method: this.editingId ? 'PUT' : 'POST', data: payload })
      const id = service.id
      this.editingId = id
      for (const mediaId of this.originalMediaIds) if (!this.images.some(image => image.id === mediaId)) await request(`/v1/services/${id}/images/${mediaId}`, { method: 'DELETE' })
      for (const image of this.images) {
        if (image.remoteUrl) continue
        const uploaded = await uploadServiceImage(id, image.path)
        image.id = uploaded.id || image.id
        image.remoteUrl = uploaded.url
      }
      const remoteImages = this.images.filter(image => image.remoteUrl)
      this.originalMediaIds = remoteImages.map(image => image.id)
      if (remoteImages.length) {
        const cover = remoteImages.find(image => image.cover) || remoteImages[0]
        await request(`/v1/services/${id}/images/layout`, { method: 'PUT', data: { mediaIds: remoteImages.map(image => image.id), coverMediaId: cover.id } })
      }
      if (status === 'published') await request(`/v1/services/${id}/publish`, { method: 'POST' })
      uni.showToast({ title: message })
      setTimeout(() => uni.navigateBack(), 350)
    } catch (error) { uni.showToast({ title: error.message || '保存失败', icon: 'none' }) }
    finally { this.setData({ saving: false }) }
  },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
page { background: #f6f5f8; }
.editor-shell { min-height: 100vh; }
.editor-nav { position: sticky; z-index: 40; top: 0; min-height: 88rpx; padding: 184rpx 28rpx 8rpx; display: grid; grid-template-columns: 88rpx minmax(0,1fr) 112rpx; align-items: center; border-bottom: 1rpx solid rgba(231,226,236,.9); background: rgba(255,255,255,.97); }
.editor-nav__close { width: 88rpx; height: 88rpx; margin: 0; padding: 0; display: flex; align-items: center; justify-content: center; border: 0; background: transparent; color: var(--color-text); font-size: 62rpx; font-weight: 300; line-height: 1; }
.editor-nav__title { min-width: 0; padding-left: 8rpx; font-size: var(--type-page); line-height: 1.25; font-weight: var(--weight-page); }
.editor-nav__publish { width: 112rpx; height: 88rpx; margin: 0; padding: 0; display: flex; align-items: center; justify-content: center; border: 0; border-radius: 999rpx; background: var(--color-primary); color: #fff; font-size: 27rpx; line-height: 1; font-weight: 800; }
.editor-nav__publish--disabled { opacity: .45; }
.composer-page { padding: 22rpx 24rpx 80rpx; }
.compose-card, .settings-card { overflow: hidden; border: 1rpx solid var(--color-border); border-radius: 30rpx; background: #fff; box-shadow: 0 10rpx 28rpx rgba(67,55,91,.05); }
.compose-card { padding: 28rpx; }
.media-head { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; margin-bottom: 20rpx; }
.media-head__title { color: var(--color-text); font-size: var(--type-card); font-weight: var(--weight-card); }
.media-head__hint { color: var(--color-muted-soft); font-size: 22rpx; }
.media-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14rpx; }
.photo, .media-add { position: relative; aspect-ratio: 1; min-width: 0; overflow: hidden; box-sizing: border-box; border-radius: 22rpx; }
.photo { border: 4rpx solid transparent; background: #f1eff5; }
.photo--cover { border-color: var(--color-primary); }
.photo__image { width: 100%; height: 100%; }
.photo__cover { position: absolute; left: 8rpx; bottom: 8rpx; padding: 5rpx 12rpx; border-radius: 999rpx; background: var(--color-primary); color: #fff; font-size: 22rpx; font-weight: 700; }
.photo__delete { position: absolute; right: 7rpx; top: 7rpx; width: 46rpx; height: 46rpx; margin: 0; padding: 0; border: 0; border-radius: 50%; background: rgba(26,21,33,.72); color: #fff; font-size: 31rpx; line-height: 42rpx; }
.media-add { min-height: 190rpx; margin: 0; padding: 20rpx 8rpx; border: 2rpx dashed #cbc5d1; background: #f8f7fa; color: var(--color-muted); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6rpx; }
.media-add__icon { position: relative; width: 58rpx; height: 44rpx; margin-bottom: 5rpx; box-sizing: border-box; border: 4rpx solid var(--color-muted); border-radius: 10rpx; }
.media-add__icon::before { content: ''; position: absolute; left: 21rpx; top: -13rpx; width: 4rpx; height: 24rpx; border-radius: 4rpx; background: var(--color-muted); }
.media-add__icon::after { content: ''; position: absolute; left: 11rpx; top: -3rpx; width: 24rpx; height: 4rpx; border-radius: 4rpx; background: var(--color-muted); }
.media-add__title { color: #3e3747; font-size: 23rpx; font-weight: 700; }
.media-add__hint { color: var(--color-muted-soft); font-size: var(--type-caption); }
.compose-title { width: 100%; height: 100rpx; margin-top: 28rpx; box-sizing: border-box; border-top: 1rpx solid var(--color-border); border-bottom: 1rpx solid var(--color-border); color: var(--color-text); font-size: var(--type-section); font-weight: var(--weight-section); }
.compose-desc { width: 100%; min-height: 270rpx; padding: 26rpx 0 8rpx; box-sizing: border-box; color: var(--color-text); font-size: 29rpx; line-height: 1.7; }
.compose-count { display: block; text-align: right; color: var(--color-muted-soft); font-size: 22rpx; }
.settings-card { margin-top: 20rpx; }
.price-row { display: grid; grid-template-columns: 1.2fr .8fr; }
.price-field { min-height: 126rpx; padding: 22rpx 26rpx; box-sizing: border-box; }
.price-field--hours { border-left: 1rpx solid var(--color-border); }
.price-field__label { display: block; color: var(--color-muted-soft); font-size: 22rpx; }
.price-field__input { margin-top: 9rpx; display: flex; align-items: baseline; gap: 6rpx; color: var(--color-cta); }
.price-field__control { width: 92rpx; min-width: 0; height: 50rpx; color: var(--color-cta); font-size: 34rpx; font-weight: 800; }
.price-field__currency { font-size: 25rpx; font-weight: 800; }
.price-field__unit { color: var(--color-muted-soft); font-size: 22rpx; }
.price-field__picker { min-height: 60rpx; margin-top: 6rpx; padding: 0 14rpx; display: flex; align-items: center; justify-content: space-between; gap: 12rpx; border: 2rpx solid var(--color-border); border-radius: 14rpx; background: var(--color-bg); color: var(--color-text); font-size: 27rpx; font-weight: 750; }
.price-field__picker .control-chevron { width: 12rpx; height: 12rpx; }
.price-breakdown { padding: 18rpx 26rpx 22rpx; border-top: 1rpx solid var(--color-border); background: var(--color-primary-soft); }
.price-breakdown__rows { display: grid; gap: 12rpx; }
.price-breakdown__line { display: flex; align-items: baseline; justify-content: space-between; gap: 18rpx; color: var(--color-muted); font-size: 23rpx; line-height: 1.4; }
.price-breakdown__amount { flex: none; color: var(--color-text); font-variant-numeric: tabular-nums; text-align: right; }
.price-breakdown__line--net { padding-top: 14rpx; border-top: 1rpx solid var(--color-border); color: var(--color-text); font-size: 27rpx; font-weight: 800; }
.price-breakdown__amount--net { color: var(--color-primary-ink); font-size: 30rpx; }
.price-breakdown__empty { display: block; color: var(--color-muted); font-size: 23rpx; }
.price-breakdown__hint { display: block; margin-top: 13rpx; color: var(--color-muted); font-size: 21rpx; line-height: 1.4; }
.setting-row { width: 100%; min-height: 116rpx; margin: 0; padding: 20rpx 26rpx; border: 0; border-top: 1rpx solid var(--color-border); border-radius: 0; background: #fff; display: flex; align-items: center; justify-content: space-between; gap: 20rpx; text-align: left; }
.setting-row__copy { flex: 1; min-width: 0; }
.setting-row__title { display: block; color: var(--color-text); font-size: var(--type-body); font-weight: var(--weight-card); }
.setting-row__value { display: block; margin-top: 6rpx; overflow: hidden; color: var(--color-muted-soft); font-size: 22rpx; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.setting-row .control-chevron { margin-right: 5rpx; }
.setting-row:active { background: var(--color-primary-soft); }
.compose-tip { display: block; margin: 18rpx 8rpx 0; color: var(--color-muted-soft); font-size: 22rpx; line-height: 1.55; }
.draft-button { min-width: 180rpx; height: 76rpx; margin: 22rpx auto 0; padding: 0 28rpx; border: 0; background: transparent; color: var(--color-primary); font-size: 25rpx; font-weight: 700; }
.sheet-mask { position: fixed; z-index: 90; inset: 0; display: flex; align-items: flex-end; background: rgba(25,20,32,.48); }
.boundary-sheet { width: 100%; max-height: 80vh; padding: 14rpx 28rpx calc(28rpx + env(safe-area-inset-bottom)); box-sizing: border-box; overflow-y: auto; border-radius: 36rpx 36rpx 0 0; background: #fff; }
.sheet-handle { width: 72rpx; height: 8rpx; margin: 0 auto 20rpx; border-radius: 8rpx; background: #dedbe5; }
.sheet-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 20rpx; }
.sheet-head__title { display: block; font-size: 34rpx; font-weight: 800; }
.sheet-head__hint { display: block; margin-top: 6rpx; color: var(--color-muted-soft); font-size: 22rpx; }
.sheet-head__close { width: 64rpx; height: 64rpx; margin: 0; padding: 0; border: 0; border-radius: 50%; background: #f4f2f7; color: var(--color-muted); font-size: 40rpx; line-height: 60rpx; }
.boundary-list { margin-top: 24rpx; }
.boundary-item { min-height: 92rpx; margin-top: 14rpx; padding: 0 12rpx 0 18rpx; display: flex; align-items: center; gap: 12rpx; border: 1rpx solid var(--color-border); border-radius: 20rpx; }
.boundary-item__index { width: 40rpx; height: 40rpx; border-radius: 50%; background: var(--color-primary-soft); color: var(--color-primary); font-size: 22rpx; font-weight: 750; line-height: 40rpx; text-align: center; }
.boundary-item__input { flex: 1; min-width: 0; height: 82rpx; font-size: 27rpx; }
.boundary-item__delete { min-width: 76rpx; height: 68rpx; margin: 0; padding: 0 10rpx; border: 0; background: transparent; color: var(--color-danger); font-size: 22rpx; }
.boundary-add, .boundary-confirm { height: 88rpx; margin: 20rpx 0 0; padding: 0; display: flex; align-items: center; justify-content: center; border-radius: 999rpx; font-size: 27rpx; line-height: 1; font-weight: 750; }
.boundary-add { border: 2rpx solid var(--color-primary); background: #fff; color: var(--color-primary); }
.boundary-confirm { border: 0; background: var(--color-primary); color: #fff; }

</style>
