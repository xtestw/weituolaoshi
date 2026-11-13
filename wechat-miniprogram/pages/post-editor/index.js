const { get, set } = require('../../utils/store')
const { validateVenue } = require('../../utils/cities')
const { guardBooking } = require('../../utils/eligibility')
const { hourlyBreakdown } = require('../../utils/pricing')

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
    place: '公共场所（具体地点待选择）',
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
    const images = (draft.images || blockImages).map((item, index) => ({ ...item, id: item.id || `image-${index}`, cover: item.cover || index === 0 }))
    const boundaries = draft.boundaries && draft.boundaries.length ? draft.boundaries : defaultBoundaries
    const questions = Array.isArray(draft.questions) ? draft.questions : defaultQuestions
    this.setData({
      editingId: draft.id,
      images,
      title: draft.title || '',
      description: draft.description || (blockText ? blockText.text : ''),
      price: String(draft.price || ''),
      priceBreakdown: hourlyBreakdown(draft.price || ''),
      minHours: Number(draft.minHours || 2),
      city: coserCity || draft.city || '上海',
      district: draft.district || '',
      placeDetails: draft.placeDetails || null,
      place: draft.place || '公共场所（具体地点待选择）',
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
    if (!get('coserCity', '')) return wx.showToast({ title: '请先定位服务城市', icon: 'none' })
    wx.chooseLocation({ success: (location) => {
      const result = validateVenue(location, this.data.city)
      if (!result.valid) return wx.showToast({ title: result.reason, icon: 'none' })
      this.setData({ place: result.place, district: result.district, placeDetails: result, dirty: true })
    } })
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
    if (!this.data.placeDetails || !validateVenue(this.data.placeDetails, this.data.city).valid) return wx.showToast({ title: '请选择开放城区内的公共场所', icon: 'none' })
    this.persist('review', '已提交审核')
  },
  persist(status, message) {
    this.setData({ saving: true })
    const posts = get('posts', [])
    const boundaries = this.data.boundaries.filter((item) => item.text.trim())
    const blocks = this.data.images.map((item) => ({ ...item, type: 'image' })).concat({ id: `text-${Date.now()}`, type: 'text', text: this.data.description })
    const record = {
      id: this.data.editingId || `post-${Date.now()}`,
      images: this.data.images,
      blocks,
      title: this.data.title.trim(),
      description: this.data.description.trim(),
      price: Number(this.data.price),
      minHours: Number(this.data.minHours),
      city: this.data.city,
      district: this.data.district,
      place: this.data.place,
      placeDetails: this.data.placeDetails,
      boundaries,
      questions: this.data.questions.filter((item) => item.text.trim()).map((item) => ({ ...item, text: item.text.trim() })),
      questionCount: this.data.questions.filter((item) => item.text.trim()).length,
      coverMark: 'COS',
      status,
      statusText: status === 'draft' ? '草稿' : '审核中',
      locked: false
    }
    const next = posts.some((item) => item.id === record.id) ? posts.map((item) => item.id === record.id ? record : item) : [record, ...posts]
    set('posts', next)
    setTimeout(() => {
      this.setData({ saving: false, dirty: false })
      wx.showToast({ title: message })
      setTimeout(() => wx.navigateBack(), 450)
    }, 450)
  }
})
