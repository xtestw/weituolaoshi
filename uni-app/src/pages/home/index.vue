<template>
<view class="safe-top home-head">
  <view class="brand-row"><text class="brand-name">次元委托</text></view>
  <view class="utility-row">
    <button :class="&quot;location &quot; + (cityPickerOpen ? 'location--open' : '')" @tap="selectCity" :aria-label="&quot;切换城市，当前&quot; + (city)" :aria-expanded="cityPickerOpen"><text>{{ city }}</text><view class="control-chevron control-chevron--down" aria-hidden="true"></view></button>
    <view class="search">
      <view class="search__icon" aria-hidden="true"></view>
      <input class="search__input" :value="keyword" @input="onKeyword" @confirm="submitSearch" confirm-type="search" placeholder="搜索角色、Coser或场景" aria-label="搜索"/>
      <button class="search__submit" @tap="submitSearch" aria-label="提交搜索">搜索</button>
    </view>
  </view>
</view>

<view v-if="cityPickerOpen" class="city-overlay" @tap="closeCityPicker">
  <view class="city-sheet" @tap.stop="stopCalendar">
    <view class="city-sheet__head"><text>选择开放城市</text><button class="city-sheet__close" @tap="closeCityPicker" aria-label="关闭城市选择">×</button></view>
    <view class="city-sheet__grid"><button v-for="(item, index) in cities" :key="item" :class="&quot;city-sheet__option &quot; + (city === item ? 'city-sheet__active' : '')" :data-city="item" @tap="chooseCity">{{ item }}</button></view>
  </view>
</view>

<view class="page home-page">
  <button class="trust-line" @tap="showRules">全女委托社区 · 预约前需成年实名认证 <text>›</text></button>
  <view class="home-tabs"><button :class="{ active: activeTab === 'services' }" @tap="activeTab = 'services'">找委托</button><button :class="{ active: activeTab === 'community' }" @tap="activeTab = 'community'">逛社区</button><text>发现同城的心动角色</text></view>
  <view v-if="activeTab === 'services'">
    <view class="home-filters">
      <picker mode="date" :start="today" :value="selectedDate || today" @change="chooseDate"><view :class="['filter-chip', { selected: selectedDate }]">{{ selectedDate ? selectedDate.slice(5) : '日期' }} ⌄</view></picker>
      <picker :range="roleOptions" @change="chooseRole"><view :class="['filter-chip', { selected: selectedRole }]">{{ selectedRole || '角色 / 作品' }} ⌄</view></picker>
      <picker :range="priceOptions" :value="priceIndex" @change="choosePrice"><view :class="['filter-chip', { selected: priceIndex }]">{{ priceIndex ? priceOptions[priceIndex] : '价格' }} ⌄</view></picker>
      <button :class="['filter-chip', { selected: availableOnly }]" @tap="toggleAvailable">有档期</button>
    </view>
    <view class="feed-summary"><text>{{ city }} · {{ left.length + right.length }} 个角色委托</text><button v-if="hasFilters" @tap="clearFilters">清空筛选</button><text v-else>为你发现</text></view>
    <view v-if="catalogError" class="home-empty"><text>委托暂时加载失败</text><button @tap="refresh">重新加载</button></view>
    <view v-else-if="loading || filtering" class="home-empty">{{ filtering ? '正在核对所选日期的真实档期…' : '正在寻找心动角色…' }}</view>
    <view v-else-if="filterError" class="home-empty"><text>部分档期暂时无法查询</text><button @tap="applyFeed">重新查询</button></view>
    <view v-else-if="left.length || right.length" class="waterfall"><view class="column"><service-card v-for="item in left" :key="item.id" :item="item" @open="openService"/></view><view class="column"><service-card v-for="item in right" :key="item.id" :item="item" @open="openService"/></view></view>
    <view v-else class="home-empty"><text>{{ hasFilters ? '暂无符合条件的委托，试试其他条件' : city + '暂无委托，可以切换城市看看' }}</text><button v-if="hasFilters" @tap="clearFilters">清空筛选</button><button v-else @tap="activeTab = 'community'">去逛社区</button></view>
    <text v-if="selectedDate && !filtering && !filterError" class="date-note">所选日期有可用时段，具体开始时间请在预约时确认。</text>
  </view>
  <view v-else class="community-section">
    <view class="community-head"><text>同城的新鲜事</text><button @tap="openMyPosts">我的帖子 ›</button></view>
    <view class="community-intro"><text>返图、心情，还有想分享的故事</text><button @tap="createPost">分享一篇</button></view>
    <view v-if="communityLoading" class="home-empty">正在加载社区内容…</view>
    <view v-else-if="communityError" class="home-empty"><text>社区暂时加载失败</text><button @tap="loadCommunityPosts">重新加载</button></view>
    <template v-else>
      <button v-for="post in communityPosts" :key="post.id" class="story-card" @tap="openCommunity(post)">
        <text class="story-author">{{ post.author || '社区用户' }} · {{ city }}</text>
        <text class="story-title">{{ post.title }}</text>
        <image v-if="post.coverUrl" class="story-cover" :src="post.coverUrl" mode="aspectFill"/>
        <text class="story-body">{{ post.body }}</text>
        <text class="story-meta">{{ post.likeCount || 0 }} 赞 · {{ post.commentCount || 0 }} 评论</text>
      </button>
      <view v-if="!communityPosts.length" class="home-empty"><text>来分享第一篇同城故事</text><button @tap="createPost">发布社区帖</button></view>
    </template>
  </view>
</view>

</template>
<script>
import ServiceCard from '../../components/service-card/index.vue'
import { loadCatalog } from '../../utils/services'
import { CITIES } from '../../utils/cities'
import { get, set } from '../../utils/store'
import { request, mediaUrl } from '../../services/api'

export default {
components: { ServiceCard },
data() { return {
    activeTab: 'services', selectedDate: '', selectedRole: '', priceIndex: 0, availableOnly: false,
    priceOptions: ['不限价格', '¥150以下', '¥150–200', '¥200以上'], catalog: [],
    today: '', loading: true, filtering: false, filterError: false, catalogError: false,
    filterVersion: 0, catalogVersion: 0, communityVersion: 0, communityLoading: false, communityError: false,
    city: '上海',
    cities: CITIES,
    cityPickerOpen: false,
    keyword: '',
    communityPosts: [],
    left: [],
    right: []
  } },
computed: {
  roleOptions() { return ['全部角色 / 作品', ...new Set(this.catalog.filter(item => (item.city || '上海') === this.city).flatMap(item => [item.characterName, item.ipName]).filter(Boolean))] },
  hasFilters() { return !!(this.selectedDate || this.selectedRole || this.priceIndex || this.availableOnly) }
},
onShow() {
    uni.showTabBar()
    this.today = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10)
    if (this.selectedDate < this.today) this.selectedDate = ''
    const city = get('currentCity', '上海')
    if (city !== this.city) { this.city = city; this.selectedRole = '' }
    this.refresh()
  },
onShareAppMessage() { return { title: '次元委托｜寻找同城可约 Coser', path: '/pages/home/index' } },
onPullDownRefresh() { this.refresh().finally(() => uni.stopPullDownRefresh()) },
methods: {
async refresh() { await Promise.all([this.loadServices(), this.loadCommunityPosts()]) },
async loadServices() {
  const version = ++this.catalogVersion
  this.loading = true; this.catalogError = false
  try { const rows = await loadCatalog(); if (version !== this.catalogVersion) return; this.catalog = [...rows]; if (!this.roleOptions.includes(this.selectedRole)) this.selectedRole = ''; await this.applyFeed() }
  catch { if (version === this.catalogVersion) { this.catalogError = true; this.left = []; this.right = [] } }
  finally { if (version === this.catalogVersion) this.loading = false }
},
async loadCommunityPosts() {
  const version = ++this.communityVersion
  this.communityLoading = true; this.communityError = false
  try {
    const rows = await request(`/v1/posts?city=${encodeURIComponent(this.city)}`)
    if (version === this.communityVersion) {
      this.communityPosts = rows.map(post => {
        const first = (post.images && post.images[0]) || null
        return { ...post, coverUrl: first ? mediaUrl(first.url) : '' }
      })
    }
  }
  catch { if (version === this.communityVersion) { this.communityPosts = []; this.communityError = true } }
  finally { if (version === this.communityVersion) this.communityLoading = false }
},
showRules() { uni.showModal({ title: '全女委托社区', content: '你可以自由浏览；下单和发布委托前需要完成成年女性身份核验。委托仅限允许的公共场所，不支持过夜。', showCancel: false, confirmText: '我知道了' }) },
createPost() { if (!uni.getStorageSync('account_token')) return uni.switchTab({ url: '/pages/profile/index' }); uni.navigateTo({ url: '/pages/community-editor/index' }) },
chooseDate(event) { this.selectedDate = event.detail.value; this.applyFeed() },
chooseRole(event) { this.selectedRole = Number(event.detail.value) ? this.roleOptions[Number(event.detail.value)] : ''; this.applyFeed() },
choosePrice(event) { this.priceIndex = Number(event.detail.value); this.applyFeed() },
toggleAvailable() { this.availableOnly = !this.availableOnly; this.applyFeed() },
clearFilters() { this.selectedDate = ''; this.selectedRole = ''; this.priceIndex = 0; this.availableOnly = false; this.applyFeed() },
openCommunity(post) { uni.navigateTo({ url: `/pages/community-detail/index?id=${post.id}` }) },
openMyPosts() { if (!uni.getStorageSync('account_token')) return uni.switchTab({ url: '/pages/profile/index' }); uni.navigateTo({ url: '/pages/community-mine/index' }) },
selectCity() {
    this.setData({ cityPickerOpen: true })
    uni.hideTabBar()
  },
closeCityPicker() {
    this.setData({ cityPickerOpen: false })
    uni.showTabBar()
  },
chooseCity(event) {
    const city = event.currentTarget.dataset.city
    if (!CITIES.includes(city)) return
    set('currentCity', city)
    getApp().globalData.currentCity = city
    this.selectedRole = ''
    this.setData({ city, cityPickerOpen: false }, () => {
      this.applyFeed()
      this.loadCommunityPosts()
    })
    uni.showTabBar()
  },
onKeyword(event) { this.setData({ keyword: event.detail.value }) },
submitSearch() { uni.navigateTo({ url: `/pages/search/index?q=${encodeURIComponent(this.keyword)}&city=${encodeURIComponent(this.city)}` }) },
async applyFeed() {
    const version = ++this.filterVersion
    const date = this.selectedDate
    this.filterError = false; this.filtering = !!date
    let items = this.catalog.filter(item => (item.city || '上海') === this.city)
    if (this.selectedRole) items = items.filter(item => item.characterName === this.selectedRole || item.ipName === this.selectedRole)
    if (this.priceIndex === 1) items = items.filter(item => item.price < 150)
    if (this.priceIndex === 2) items = items.filter(item => item.price >= 150 && item.price <= 200)
    if (this.priceIndex === 3) items = items.filter(item => item.price > 200)
    if (this.availableOnly) items = items.filter(item => item.available)
    if (date) {
      const matches = []; let failed = false; let cursor = 0
      // Bound concurrency until the catalog API supports date filtering.
      await Promise.all(Array.from({ length: Math.min(4, items.length) }, async () => {
        while (cursor < items.length && version === this.filterVersion) {
          const item = items[cursor++]
          try {
            const data = await request(`/v1/cosers/${encodeURIComponent(item.id)}/availability`)
            if ((data.slots || []).some(slot => String(slot.startAt).slice(0, 10) === date && (new Date(slot.endAt) - new Date(slot.startAt)) / 3600000 >= (Number(item.minHours) || 1))) matches.push(item.id)
          } catch { failed = true }
        }
      }))
      if (version !== this.filterVersion) return
      this.filterError = failed
      items = items.filter(item => matches.includes(item.id)).map(item => ({ ...item, availabilityLabel: `${date.slice(5)} 有可约时段` }))
    }
    if (version !== this.filterVersion) return
    this.left = items.filter((_, index) => index % 2 === 0)
    this.right = items.filter((_, index) => index % 2 === 1)
    this.filtering = false
  },
stopCalendar() {},
openService(event) { uni.navigateTo({ url: `/pages/coser/index?id=${(event.id || event.detail?.id)}` }) },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style scoped>
.community-section { margin-bottom: 28rpx; }.community-head { display: flex; justify-content: space-between; align-items: center; padding: 20rpx 0; font-size: 30rpx; font-weight: 700; }.community-head button { margin: 0; background: transparent; color: #6d28d9; font-size: 24rpx; }.community-card { display: block; width: 100%; text-align: left; background: white; border-radius: 20rpx; padding: 22rpx; margin-bottom: 14rpx; }.community-card text { display: block; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }.community-card text:first-child { font-weight: 700; }.community-card text:nth-child(2) { color: #777; font-size: 24rpx; }.community-meta { margin-top: 8rpx; color: #6d28d9 !important; font-size: 22rpx !important; }
.home-head { position: sticky; z-index: 30; top: 0; padding-left: 28rpx; padding-right: 28rpx; padding-bottom: 18rpx; border-bottom: 1rpx solid rgba(231,226,236,.85); background: rgba(255,255,255,.97); box-shadow: 0 8rpx 24rpx rgba(67,55,91,.04); }
.brand-row { height: 84rpx; display: flex; align-items: center; }
.brand-name { font-size: var(--type-page); line-height: 1.25; font-weight: var(--weight-page); letter-spacing: 1rpx; }
.utility-row { display: flex; align-items: center; gap: 14rpx; }
.location { flex: none; min-width: 116rpx; height: 80rpx; margin: 0; padding: 0 14rpx; border: 2rpx solid transparent; border-radius: 20rpx; background: transparent; color: var(--color-text); font-size: 27rpx; font-weight: 750; line-height: 1; display: flex; align-items: center; justify-content: center; gap: 12rpx; white-space: nowrap; }
.location text { display: block; line-height: 1; }
.location--open { border-color: #d8b4fe; background: var(--color-primary-soft); color: var(--color-primary-ink); }
.location--open .control-chevron { color: var(--color-primary-ink); transform: rotate(225deg) translateY(-3rpx); }
.search { flex: 1; min-width: 0; height: 88rpx; box-sizing: border-box; padding: 0 6rpx 0 22rpx; display: flex; align-items: center; gap: 14rpx; border: 2rpx solid #e2dfea; border-radius: 999rpx; background: #f8f8fc; }
.search__icon { position: relative; width: 26rpx; height: 26rpx; box-sizing: border-box; border: 4rpx solid var(--color-muted); border-radius: 50%; }
.search__icon::after { content: ''; position: absolute; right: -10rpx; bottom: -7rpx; width: 13rpx; height: 4rpx; border-radius: 4rpx; background: var(--color-muted); transform: rotate(45deg); }
.search__input { flex: 1; min-width: 0; height: 80rpx; line-height: 80rpx; font-size: 25rpx; }
.search__submit { flex: none; min-width: 88rpx; height: 72rpx; margin: 0; padding: 0 12rpx; display: flex; align-items: center; justify-content: center; border: 0; border-radius: 999rpx; background: var(--color-primary); color: #fff; font-size: 24rpx; line-height: 1; font-weight: 700; white-space: nowrap; }
.share-icon { position: relative; width: 34rpx; height: 30rpx; box-sizing: border-box; border: 3rpx solid currentColor; border-top: 0; border-radius: 5rpx; }
.share-icon::before { content: ''; position: absolute; left: 13rpx; top: -13rpx; width: 4rpx; height: 20rpx; border-radius: 4rpx; background: currentColor; }
.share-icon::after { content: ''; position: absolute; left: 8rpx; top: -13rpx; width: 11rpx; height: 11rpx; border-left: 3rpx solid currentColor; border-top: 3rpx solid currentColor; transform: rotate(45deg); }
.home-page { padding-top: 14rpx; }
.waterfall { margin-top: 18rpx; display: flex; gap: 18rpx; align-items: flex-start; }
.column { width: calc(50% - 9rpx); }
.city-overlay { position: fixed; z-index: 95; inset: 0; display: flex; align-items: flex-end; background: rgba(25,20,32,.48); }
.city-sheet { width: 100%; max-height: 85vh; overflow-y: auto; padding: 30rpx 28rpx calc(36rpx + env(safe-area-inset-bottom)); box-sizing: border-box; border-radius: 36rpx 36rpx 0 0; background: #fff; }
.city-sheet__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 22rpx; font-size: 32rpx; font-weight: 750; }
.city-sheet__close { width: 80rpx; height: 80rpx; margin: 0; padding: 0; border: 0; border-radius: 50%; background: var(--color-soft); color: var(--color-muted); font-size: 38rpx; display: flex; align-items: center; justify-content: center; }
.city-sheet__grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16rpx; }
.city-sheet__option { width: 100%; min-height: 88rpx; margin: 0; padding: 0; display: flex; align-items: center; justify-content: center; border: 1rpx solid var(--color-border); border-radius: 18rpx; background: var(--color-bg); color: var(--color-text); font-size: 28rpx; line-height: 1; }
.city-sheet__active { border-color: var(--color-primary); background: var(--color-primary-soft); color: var(--color-primary-ink); font-weight: 750; }


.trust-line { width: 100%; margin: 0; padding: 0; min-height: 64rpx; display: flex; justify-content: space-between; align-items: center; background: transparent; color: #80738e; font-size: 22rpx; }
.home-tabs { display: flex; align-items: center; gap: 30rpx; margin-bottom: 20rpx; }
.home-tabs button { margin: 0; padding: 0; background: transparent; font-size: 32rpx; color: #93879d; border-radius: 0; }
.home-tabs button.active { color: #35213f; font-weight: 800; box-shadow: inset 0 -6rpx #8b5cf6; }
.home-tabs > text { margin-left: auto; color: #a091ab; font-size: 21rpx; }
.home-filters { display: flex; align-items: center; gap: 10rpx; }
.home-filters picker { flex: 1; min-width: 0; }
.filter-chip { margin: 0; padding: 0 12rpx; min-height: 80rpx; line-height: 80rpx; border-radius: 18rpx; background: #fff; color: #71647e; font-size: 23rpx; text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.filter-chip.selected { background: #eee7fc; color: #6d28d9; font-weight: 700; }
.feed-summary { display: flex; justify-content: space-between; align-items: center; min-height: 72rpx; color: #93879d; font-size: 22rpx; }
.feed-summary button { margin: 0; padding: 0 8rpx; background: transparent; color: #7c4ec1; font-size: 23rpx; }
.waterfall { margin-top: 0; }
.home-empty { display: flex; flex-direction: column; align-items: center; gap: 20rpx; padding: 70rpx 24rpx; border-radius: 24rpx; background: #fff; text-align: center; font-size: 26rpx; color: #80738e; }
.home-empty button,.community-intro button { margin: 0; padding: 0 26rpx; border-radius: 32rpx; background: #eee7fc; color: #6d28d9; font-size: 24rpx; }
.community-intro { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; color: #93879d; font-size: 23rpx; }
.story-card { width: 100%; margin: 0 0 20rpx; padding: 28rpx; border-radius: 24rpx; background: #fff; text-align: left; line-height: 1.5; }
.story-card text { display: block; }
.story-author,.story-meta { color: #93879d; font-size: 23rpx; }
.story-title { margin-top: 16rpx; font-size: 30rpx; font-weight: 700; color: #35213f; }
.story-cover { width: 100%; height: 280rpx; margin-top: 16rpx; border-radius: 18rpx; background: #f1eff5; }
.story-body { margin: 12rpx 0 24rpx; font-size: 26rpx; color: #71647e; }
.story-title,.story-body { display: -webkit-box !important; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.story-body { -webkit-line-clamp: 3; }
.date-note { display: block; margin-top: 20rpx; color: #93879d; font-size: 23rpx; }
button::after { border: none; }
</style>
