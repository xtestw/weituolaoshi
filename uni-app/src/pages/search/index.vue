<template>
<view class="searchbar">
  <input :value="keyword" @input="onKeyword" @confirm="submit" confirm-type="search" placeholder="搜角色、Coser或场景" aria-label="搜索关键词"/>
  <button @tap="submit">搜索</button>
</view>
<scroll-view class="filters" scroll-x enhanced show-scrollbar="false">
  <button v-for="(item, index) in filters" :key="item.key" :class="&quot;filter &quot; + (item.value ? 'filter--active' : '') + &quot; &quot; + (filterSheetOpen && filterSheetKey === item.key ? 'filter--open' : '')" :data-key="item.key" @tap="chooseFilter" :aria-expanded="filterSheetOpen && filterSheetKey === item.key"><text class="filter__label">{{ item.value || item.label }}</text><view class="control-chevron control-chevron--down filter__chevron" aria-hidden="true"></view></button>
</scroll-view>
<view v-if="filterSheetOpen" class="filter-overlay" @tap="closeFilterSheet"><view class="filter-sheet" @tap.stop="stopTap"><view class="filter-sheet__handle"></view><view class="filter-sheet__head"><text>{{ filterSheetTitle }}</text><button @tap="closeFilterSheet" aria-label="关闭筛选">×</button></view><scroll-view scroll-y class="filter-sheet__list"><button v-for="(item, index) in filterSheetOptions" :key="item" :class="&quot;filter-option &quot; + (filterSheetSelected === item ? 'filter-option--selected' : '')" :data-value="item" @tap="selectFilterOption" :aria-selected="filterSheetSelected === item"><text>{{ item }}</text><view class="filter-option__mark" aria-hidden="true"></view></button></scroll-view></view></view>
<view class="page results-page">
  <view class="community-head">社区帖子 · {{ communityResults.length }}</view>
  <button v-for="post in communityResults" :key="post.id" class="community-result" @tap="openCommunity(post)"><text>{{ post.title }}</text><text>{{ post.author || '用户' }} · {{ post.body }}</text><text>赞 {{ post.likeCount || 0 }} · 评 {{ post.commentCount || 0 }}</text></button>
  <view class="community-head">Coser角色委托 · {{ results.length }}</view>
  <view v-if="catalogError" class="empty">网络暂不可用，正在展示示例内容</view>
  <view class="summary"><text class="summary__copy">{{ city }}找到 {{ results.length }} 位可预约 Coser · 已优先展示实名认证与高履约率</text><button @tap="clearFilters">重置</button></view>
  <view v-if="results.length" class="grid">
    <service-card v-for="(item, index) in results" :key="item.id" :item="item" @open="openService"/>
  </view>
  <view v-else class="empty"><text>暂时没有符合条件的Coser</text><button class="btn btn--primary" @tap="clearFilters">清空筛选</button></view>
</view>

</template>
<script>
import ServiceCard from '../../components/service-card/index.vue'
import { services, loadCatalog } from '../../utils/services'
import { DISTRICTS, CITIES } from '../../utils/cities'
import { get } from '../../utils/store'
import { request } from '../../services/api'

const filterOptions = {
  time: ['全部时间', '今天可约', '本周可约', '周末可约'],
  price: ['不限价格', '¥150以下', '¥150–200', '¥200以上'],
  credit: ['不限信用', '信用极好', '4.8分以上']
}

export default {
components: { ServiceCard },
data() { return {
    keyword: '',
    communityResults: [],
    city: '上海',
    filters: [
      { key: 'time', label: '时间', value: '' },
      { key: 'price', label: '价格', value: '' },
      { key: 'district', label: '区域', value: '' },
      { key: 'credit', label: '信用', value: '' }
    ],
    filterSheetOpen: false,
    filterSheetKey: '',
    filterSheetTitle: '',
    filterSheetOptions: [],
    filterSheetSelected: '',
    results: services
  } },
onLoad(options) {
    const city = CITIES.includes(options.city) ? options.city : get('currentCity', '上海')
    this.setData({ keyword: decodeURIComponent(options.q || ''), city }, this.applyFilters)
    loadCatalog().then(() => this.applyFilters()).catch(() => this.setData({ catalogError: true }))
  },
methods: {
openCommunity(post) { uni.navigateTo({ url: `/pages/community-detail/index?id=${post.id}` }) },
onKeyword(event) { this.setData({ keyword: event.detail.value }) },
submit() { this.applyFilters() },
chooseFilter(event) {
    const key = event.currentTarget.dataset.key
    const options = key === 'district' ? [`全${this.city}`, ...DISTRICTS[this.city]] : filterOptions[key]
    const label = this.filters.find((item) => item.key === key).label
    this.setData({ filterSheetOpen: true, filterSheetKey: key, filterSheetTitle: `选择${label}`, filterSheetOptions: options, filterSheetSelected: this.filters.find((item) => item.key === key).value })
  },
closeFilterSheet() { this.setData({ filterSheetOpen: false }) },
stopTap() {},
selectFilterOption(event) {
    const value = event.currentTarget.dataset.value
    const key = this.filterSheetKey
    const filters = this.filters.map((item) => item.key === key ? { ...item, value } : item)
    this.setData({ filters, filterSheetOpen: false }, this.applyFilters)
  },
clearFilters() { this.setData({ filters: this.filters.map((item) => ({ ...item, value: '' })) }, this.applyFilters) },
applyFilters() {
    request(`/v1/posts?q=${encodeURIComponent(this.keyword.trim())}&city=${encodeURIComponent(this.city)}`).then(rows => this.setData({ communityResults: rows })).catch(() => this.setData({ communityResults: [] }))
    const selected = Object.fromEntries(this.filters.map((item) => [item.key, item.value]))
    const keyword = this.keyword.trim().toLowerCase()
    let results = services.filter((item) => (item.city || '上海') === this.city && (!keyword || `${item.title}${item.coser}${item.district}`.toLowerCase().includes(keyword)))
    if (selected.time && selected.time !== '全部时间') results = results.filter((item) => item.available)
    if (selected.price === '¥150以下') results = results.filter((item) => item.price < 150)
    if (selected.price === '¥150–200') results = results.filter((item) => item.price >= 150 && item.price <= 200)
    if (selected.price === '¥200以上') results = results.filter((item) => item.price > 200)
    if (selected.district && selected.district !== `全${this.city}`) results = results.filter((item) => item.district === selected.district)
    if (selected.credit === '信用极好') results = results.filter((item) => item.credit === 'excellent')
    if (selected.credit === '4.8分以上') results = results.filter((item) => item.rating >= 4.8)
    this.setData({ results })
  },
openService(event) { uni.navigateTo({ url: `/pages/coser/index?id=${event.detail.id}` }) },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style>
.community-head { padding: 22rpx 0; font-weight: 700; font-size: 30rpx; }.community-result { display: block; width: 100%; padding: 22rpx; margin-bottom: 14rpx; border-radius: 18rpx; background: white; text-align: left; }.community-result text { display: block; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }.community-result text:nth-child(2) { color: #777; font-size: 23rpx; }.community-result text:last-child { color: #6d28d9; font-size: 22rpx; }
.searchbar { height: 104rpx; box-sizing: border-box; padding: 10rpx 28rpx; display: flex; align-items: center; gap: 12rpx; background: #fff; }
.searchbar input { flex: 1; min-width: 0; height: 84rpx; box-sizing: border-box; padding: 0 24rpx; border-radius: 999rpx; background: var(--color-soft); font-size: 27rpx; }
.searchbar button { min-width: 104rpx; min-height: 84rpx; margin: 0; padding: 0; display: flex; align-items: center; justify-content: center; border: 0; background: transparent; color: var(--color-primary); font-size: 27rpx; font-weight: 700; line-height: 1; }
.filters { box-sizing: border-box; width: 100%; padding: 12rpx 28rpx 16rpx; white-space: nowrap; border-bottom: 1rpx solid var(--color-border); background: #fff; }
.filter { min-height: 84rpx; margin-right: 12rpx; padding: 0 24rpx; display: inline-flex; align-items: center; justify-content: center; gap: 14rpx; vertical-align: top; border: 2rpx solid var(--color-border); border-radius: 22rpx; background: #fff; color: var(--color-muted); font-size: 25rpx; line-height: 1; font-weight: 650; transition: background-color .18s ease, border-color .18s ease; }
.filter__label { max-width: 190rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.filter__chevron { width: 12rpx; height: 12rpx; color: currentColor; }
.filter__fixed { color: var(--color-muted); font-size: 20rpx; font-weight: 600; }
.filter--active,.filter--open { border-color: #d8b4fe; background: var(--color-primary-soft); color: var(--color-primary-ink); }
.filter--open .filter__chevron { transform: rotate(225deg) translateY(-3rpx); }
.filter:active { background: var(--color-primary-soft); }
.results-page { padding-top: 20rpx; }
.summary { margin-bottom: 20rpx; display: flex; justify-content: space-between; align-items: center; gap: 16rpx; color: var(--color-muted); font-size: 23rpx; }
.summary__copy { flex: 1; min-width: 0; line-height: 1.55; }
.summary button { min-width: 88rpx; min-height: 72rpx; margin: 0; padding: 0 14rpx; border: 0; background: transparent; color: var(--color-primary); font-size: 24rpx; }
.grid { columns: 2; column-gap: 18rpx; }
.grid service-card { display: inline-block; width: 100%; break-inside: avoid; }
.empty .btn { width: 260rpx; margin: 28rpx auto 0; }
.filter-overlay { position: fixed; z-index: 90; inset: 0; display: flex; align-items: flex-end; background: rgba(25,20,32,.48); }
.filter-sheet { width: 100%; max-height: 76vh; padding: 14rpx 28rpx calc(28rpx + env(safe-area-inset-bottom)); box-sizing: border-box; border-radius: 36rpx 36rpx 0 0; background: #fff; box-shadow: 0 -18rpx 60rpx rgba(37,28,55,.16); }
.filter-sheet__handle { width: 72rpx; height: 8rpx; margin: 0 auto 16rpx; border-radius: 8rpx; background: #dedbe5; }
.filter-sheet__head { min-height: 88rpx; display: flex; align-items: center; justify-content: space-between; gap: 16rpx; color: var(--color-text); font-size: 32rpx; font-weight: 750; }
.filter-sheet__head button { width: 72rpx; height: 72rpx; margin: 0; padding: 0; border: 0; border-radius: 50%; background: var(--color-bg); color: var(--color-muted); font-size: 38rpx; display: flex; align-items: center; justify-content: center; }
.filter-sheet__list { max-height: 56vh; }
.filter-option { width: 100%; min-height: 92rpx; margin: 6rpx 0; padding: 0 22rpx; display: flex; align-items: center; justify-content: space-between; gap: 16rpx; border: 2rpx solid transparent; border-radius: 18rpx; background: #fff; color: var(--color-text); text-align: left; font-size: 28rpx; }
.filter-option--selected { border-color: #d8b4fe; background: var(--color-primary-soft); color: var(--color-primary-ink); font-weight: 750; }
.filter-option__mark { width: 28rpx; height: 28rpx; flex: none; border: 3rpx solid var(--color-border); border-radius: 50%; }
.filter-option--selected .filter-option__mark { border: 9rpx solid var(--color-primary); }

</style>
