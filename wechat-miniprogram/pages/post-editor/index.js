const { get, set } = require('../../utils/store')
const { validateVenue } = require('../../utils/cities')
const { guardBooking } = require('../../utils/eligibility')
const { hourlyBreakdown } = require('../../utils/pricing')
const { request, uploadServiceImage } = require('../../services/api')

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

Page({
  data: {
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
  },
  onLoad(options) {
    const menu = wx.getMenuButtonBoundingClientRect ? wx.getMenuButtonBoundingClientRect() : null
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
  onField(event) {
    const field = event.currentTarget.dataset.field
    const value = event.detail.value
    this.setData({ [field]: value, ...(field === 'price' ? { priceBreakdown: hourlyBreakdown(value) } : {}), dirty: true })
  },
  onMinHours(event) {
    this.setData({ minHours: this.data.minHourOptions[event.detail.value], dirty: true })
  },
  addImage() {
    const remaining = 9 - this.data.images.length
    if (remaining <= 0) return wx.showToast({ title: '最多上传9张照片', icon: 'none' })
    wx.chooseMedia({
      count: remaining,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: ({ tempFiles }) => {
        const additions = tempFiles.map((file, index) => ({
          id: `image-${Date.now()}-${index}`,
          path: file.tempFilePath,
          cover: this.data.images.length === 0 && index === 0
        }))
        this.setData({ images: this.data.images.concat(additions), dirty: true })
      }
    })
  },
  setCover(event) {
    const selected = Number(event.currentTarget.dataset.index)
    this.setData({ images: this.data.images.map((item, index) => ({ ...item, cover: index === selected })), dirty: true })
    wx.showToast({ title: '已设为封面', icon: 'none' })
  },
  removeImage(event) {
    const selected = Number(event.currentTarget.dataset.index)
    const images = this.data.images.filter((_, index) => index !== selected)
    if (images.length && !images.some((item) => item.cover)) images[0] = { ...images[0], cover: true }
    this.setData({ images, dirty: true })
  },
  choosePlace() {
    if (!get('coserCity', '')) return wx.showToast({ title: '请先选择服务城市', icon: 'none' })
    wx.chooseLocation({ success: (location) => {
      const result = validateVenue(location, this.data.city)
      if (!result.valid) return wx.showToast({ title: result.reason, icon: 'none' })
      this.setData({ place: result.place, district: result.district, placeDetails: result, dirty: true })
    }, fail: () => wx.showToast({ title: '请在地图中选择公共场所', icon: 'none' }) })
  },
  openBoundaryEditor() { this.setData({ boundarySheetOpen: true }) },
  closeBoundaryEditor() { this.setData({ boundarySheetOpen: false }) },
  stopTap() {},
  addBoundary() {
    if (this.data.boundaries.length >= 8) return wx.showToast({ title: '最多设置8条', icon: 'none' })
    this.setData({ boundaries: this.data.boundaries.concat({ id: `b-${Date.now()}`, text: '' }), dirty: true })
  },
  editBoundary(event) {
    this.setData({ [`boundaries[${event.currentTarget.dataset.index}].text`]: event.detail.value, dirty: true })
  },
  removeBoundary(event) {
    if (this.data.boundaries.length <= 1) return wx.showToast({ title: '至少保留一条边界', icon: 'none' })
    this.setData({ boundaries: this.data.boundaries.filter((_, index) => index !== Number(event.currentTarget.dataset.index)), dirty: true })
  },
  confirmBoundaries() {
    const boundaries = this.data.boundaries.filter((item) => item.text.trim())
    if (!boundaries.length) return wx.showToast({ title: '请至少填写一条边界', icon: 'none' })
    this.setData({ boundaries, boundarySummary: this.makeBoundarySummary(boundaries), boundarySheetOpen: false, dirty: true })
  },
  makeBoundarySummary(boundaries) {
    const labels = boundaries.filter((item) => item.text.trim()).map((item) => item.text.trim())
    return labels.slice(0, 3).join(' · ') + (labels.length > 3 ? ` · 共${labels.length}条` : '')
  },
  editQuestions() { this.setData({ questionSheetOpen: true }) },
  closeQuestionEditor() { this.setData({ questionSheetOpen: false }) },
  addQuestion() {
    if (this.data.questions.length >= 8) return wx.showToast({ title: '最多设置8个问题', icon: 'none' })
    const questions = this.data.questions.concat({ id: `q-${Date.now()}`, text: '' })
    this.setData({ questions, questionCount: countQuestions(questions), dirty: true })
  },
  editQuestion(event) {
    const index = Number(event.currentTarget.dataset.index)
    const questions = this.data.questions.map((item, itemIndex) => itemIndex === index ? { ...item, text: event.detail.value } : item)
    this.setData({ questions, questionCount: countQuestions(questions), dirty: true })
  },
  removeQuestion(event) {
    const index = Number(event.currentTarget.dataset.index)
    const questions = this.data.questions.filter((_, itemIndex) => itemIndex !== index)
    this.setData({ questions, questionCount: countQuestions(questions), dirty: true })
  },
  confirmQuestions() {
    const questions = this.data.questions.filter((item) => item.text.trim()).map((item) => ({ ...item, text: item.text.trim() }))
    this.setData({ questions, questionCount: countQuestions(questions), questionSheetOpen: false, dirty: true })
  },
  openSchedule() { wx.navigateTo({ url: '/pages/b-calendar/index' }) },
  closeEditor() {
    if (!this.data.dirty) return wx.navigateBack()
    wx.showActionSheet({
      itemList: ['保存草稿并退出', '不保存直接退出'],
      success: ({ tapIndex }) => tapIndex === 0 ? this.persist('draft', '已保存草稿') : wx.navigateBack()
    })
  },
  saveDraft() { this.persist('draft', '已保存草稿') },
  publish() {
    if (!guardBooking()) return
    if (get('coserVerification', {}).status !== 'verified') return wx.showModal({ title: '请先完成Coser认证', content: '认证通过后才能发布服务帖子。', showCancel: false })
    if (!this.data.images.length) return wx.showToast({ title: '请至少添加一张照片', icon: 'none' })
    if (!this.data.title.trim()) return wx.showToast({ title: '请填写帖子标题', icon: 'none' })
    if (!this.data.description.trim()) return wx.showToast({ title: '请填写帖子正文', icon: 'none' })
    if (!hourlyBreakdown(this.data.price)) return wx.showToast({ title: '请填写有效的小时价格，最多两位小数', icon: 'none' })
    this.persist('published', '发布成功')
  },
  async persist(status, message) {
    this.setData({ saving: true })
    try {
      if (!wx.getStorageSync('account_token')) throw new Error('请先登录')
      const d = this.data
      const payload = { title: d.title.trim(), description: d.description.trim(), city: d.city, place: d.place, pricePerHour: Math.round(Number(d.price) * 100), minHours: Number(d.minHours), boundaries: d.boundaries.filter(item => item.text.trim()).map(item => item.text.trim()), questions: d.questions.filter(item => item.text.trim()).map(item => item.text.trim()) }
      if (!payload.title || !payload.description || !payload.pricePerHour) throw new Error('请填写标题、正文和价格')
      const service = await request(d.editingId ? `/v1/services/${d.editingId}` : '/v1/services', { method: d.editingId ? 'PUT' : 'POST', data: payload })
      this.setData({ editingId: service.id })
      for (const mediaId of d.originalMediaIds) if (!d.images.some(image => image.id === mediaId)) await request(`/v1/services/${service.id}/images/${mediaId}`, { method: 'DELETE' })
      this.setData({ originalMediaIds: d.images.filter(image => image.remoteUrl).map(image => image.id) })
      for (const image of d.images) if (!image.remoteUrl) { const uploaded = await uploadServiceImage(service.id, image.path); image.remoteUrl = uploaded.url }
      if (status === 'published') await request(`/v1/services/${service.id}/publish`, { method: 'POST' })
      wx.showToast({ title: message })
      setTimeout(() => wx.navigateBack(), 350)
    } catch(error) { wx.showToast({ title: error.message || '保存失败', icon: 'none' }) }
    finally { this.setData({ saving: false }) }
  }
})
