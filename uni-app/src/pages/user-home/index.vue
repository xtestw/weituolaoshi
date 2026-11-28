<template>
  <view class="user-home">
    <view v-if="loading" class="state">正在打开主页…</view>
    <view v-else-if="error" class="state"><text>{{ error }}</text><button @tap="reload">重新加载</button></view>
    <template v-else-if="profile">
      <view class="cover"><image v-if="profile.coverUrl" :src="mediaUrl(profile.coverUrl)" mode="aspectFill"/><text v-else class="cover-caption">把喜欢的日常，留在这里</text><button v-if="isOwner" class="cover-edit" @tap="editProfile">更换封面</button></view>
      <view class="identity">
        <view class="identity-row"><view><text class="name">{{ profile.nickname || '社区用户' }}</text><text class="small">{{ profile.postCount || 0 }} 条公开动态</text></view><image v-if="profile.avatarUrl" class="avatar" :src="mediaUrl(profile.avatarUrl)" mode="aspectFill"/><view v-else class="avatar avatar-placeholder">{{ (profile.nickname || '我').slice(0, 1) }}</view></view>
        <text class="bio">{{ profile.bio || (isOwner ? '写一句介绍，让大家认识你。' : '这个人还没有填写简介。') }}</text>
        <view class="profile-actions"><button v-if="isOwner" @tap="editProfile">编辑资料</button><button v-if="isOwner" class="primary" @tap="publish">发动态</button><button open-type="share">分享主页</button></view>
      </view>
      <view class="timeline">
        <view class="timeline-head"><text>日常动态</text><button v-if="isOwner" @tap="manage">管理动态与草稿 ›</button></view>
        <text v-if="isOwner" class="visibility">已发布的动态会展示在个人主页和社区，所有人可见。</text>
        <view v-if="!posts.length && !timelineError" class="state"><text>{{ isOwner ? '记录你的第一条日常吧' : '还没有公开动态' }}</text><button v-if="isOwner" @tap="publish">分享此刻</button></view>
        <view v-for="post in posts" :key="post.id" class="moment">
          <view class="date"><text>{{ day(post.createdAt) }}</text><text>{{ month(post.createdAt) }}</text></view>
          <view class="moment-main">
            <text v-if="post.title && post.title !== post.body.slice(0, 40)" class="moment-title" @tap="openPost(post)">{{ post.title }}</text>
            <text class="moment-body" @tap="openPost(post)">{{ post.body }}</text>
            <view v-if="post.images && post.images.length" :class="['images', { single: post.images.length === 1 }]"><image v-for="(item, index) in post.images" :key="item.id || index" :src="mediaUrl(item.url)" mode="aspectFill" @tap="preview(post, index)"/></view>
            <view class="moment-meta"><text>{{ post.city || '' }}</text><text>{{ time(post.createdAt) }}</text></view>
            <view class="moment-actions"><button :class="{ liked: post.liked }" :disabled="busyId === post.id" @tap="like(post)">{{ post.liked ? '已赞' : '点赞' }} {{ post.likeCount || 0 }}</button><button @tap="openPost(post)">评论 {{ post.commentCount || 0 }}</button><button v-if="isOwner" @tap="editPost(post)">编辑</button><button v-if="isOwner" class="delete" @tap="remove(post)">删除</button></view>
          </view>
        </view>
        <view v-if="timelineError" class="state"><text>{{ timelineError }}</text><button @tap="loadMore">重试</button></view>
        <button v-else-if="hasMore" class="load-more" :loading="loadingMore" :disabled="loadingMore" @tap="loadMore">{{ loadingMore ? '加载中' : '查看更多动态' }}</button>
        <text v-else-if="posts.length" class="end">这些是目前的全部日常</text>
      </view>
    </template>
  </view>
</template>
<script>
import { request, mediaUrl } from '../../services/api'
export default {
  data() { return { userId: '', meId: '', profile: null, posts: [], loading: true, error: '', timelineError: '', hasMore: false, loadingMore: false, busyId: '', generation: 0 } },
  computed: { isOwner() { return !!this.meId && this.meId === this.userId } },
  onLoad(options) { this.userId = options.id || (uni.getStorageSync('account_user') || {}).id || '' },
  onShow() { this.meId = uni.getStorageSync('account_token') ? (uni.getStorageSync('account_user') || {}).id || '' : ''; this.reload() },
  onPullDownRefresh() { this.reload().finally(() => uni.stopPullDownRefresh()) },
  onReachBottom() { if (this.hasMore && !this.loading) this.loadMore() },
  onShareAppMessage() { return { title: `${this.profile?.nickname || '朋友'}的个人主页`, path: `/pages/user-home/index?id=${encodeURIComponent(this.userId)}` } },
  methods: {
    mediaUrl,
    day(value) { return String(value || '').slice(8, 10) },
    month(value) { return String(value || '').slice(0, 7).replace('-', '年') + '月' },
    time(value) { return String(value || '').slice(11, 16) },
    async reload() {
      const generation = ++this.generation
      this.loading = true; this.error = ''; this.timelineError = ''; this.loadingMore = false
      if (!this.userId) { this.error = '请先登录后查看自己的主页'; this.loading = false; return }
      try {
        const [profile, timeline] = await Promise.all([request(`/v1/users/${encodeURIComponent(this.userId)}/profile`), request(`/v1/posts/by-user/${encodeURIComponent(this.userId)}`)])
        if (generation !== this.generation) return
        this.profile = profile; this.posts = timeline.items; this.hasMore = timeline.hasMore
        uni.setNavigationBarTitle({ title: `${profile.nickname || '朋友'}的主页` })
      } catch (error) { if (generation === this.generation) this.error = error.statusCode === 404 ? '主页不存在或暂不可访问' : '主页加载失败，请稍后重试' }
      finally { if (generation === this.generation) this.loading = false }
    },
    async loadMore() {
      if (this.loadingMore) return
      const generation = this.generation
      this.loadingMore = true; this.timelineError = ''
      try {
        const result = await request(`/v1/posts/by-user/${encodeURIComponent(this.userId)}?offset=${this.posts.length}`)
        if (generation !== this.generation) return
        const ids = new Set(this.posts.map(post => post.id))
        this.posts.push(...result.items.filter(post => !ids.has(post.id))); this.hasMore = result.hasMore
      } catch (_) { if (generation === this.generation) this.timelineError = '动态加载失败，请重试' }
      finally { if (generation === this.generation) this.loadingMore = false }
    },
    editProfile() { uni.navigateTo({ url: '/pages/profile-edit/index' }) },
    publish() { uni.navigateTo({ url: '/pages/community-editor/index?moment=1' }) },
    manage() { uni.navigateTo({ url: '/pages/community-mine/index' }) },
    editPost(post) { uni.navigateTo({ url: `/pages/community-editor/index?moment=1&id=${encodeURIComponent(post.id)}` }) },
    openPost(post) { uni.navigateTo({ url: `/pages/community-detail/index?id=${encodeURIComponent(post.id)}` }) },
    preview(post, index) { const urls = post.images.map(item => mediaUrl(item.url)); uni.previewImage({ urls, current: urls[index] }) },
    async like(post) {
      if (!uni.getStorageSync('account_token')) return uni.switchTab({ url: '/pages/profile/index' })
      if (this.busyId) return
      this.busyId = post.id
      try { const updated = await request(`/v1/posts/${encodeURIComponent(post.id)}/likes`, { method: post.liked ? 'DELETE' : 'POST' }); this.posts = this.posts.map(item => item.id === post.id ? updated : item) }
      catch (error) { uni.showToast({ title: error.message || '操作失败', icon: 'none' }) }
      finally { this.busyId = '' }
    },
    remove(post) { uni.showModal({ title: '删除这条动态？', content: '删除后，个人主页和社区都不再展示。', success: async ({ confirm }) => {
      if (!confirm) return
      try { await request(`/v1/posts/${encodeURIComponent(post.id)}`, { method: 'DELETE' }); await this.reload() }
      catch (error) { uni.showToast({ title: error.message || '删除失败', icon: 'none' }) }
    } }) }
  }
}
</script>
<style scoped>
.user-home { min-height: 100vh; background: #fff; padding-bottom: 60rpx; color: #332c3b; }
.cover { position: relative; height: 380rpx; background: linear-gradient(130deg,#ded5ef,#bcb6db 65%,#e6cedd); overflow: hidden; }
.cover > image { width: 100%; height: 100%; }
.cover-caption { position: absolute; left: 40rpx; top: 140rpx; color: #fff; font-size: 30rpx; letter-spacing: 3rpx; }
.cover-edit { position: absolute; right: 28rpx; top: 24rpx; padding: 0 20rpx; margin: 0; border-radius: 30rpx; background: rgba(0,0,0,.25); color: white; font-size: 22rpx; }
.identity { padding: 0 36rpx 30rpx; }
.identity-row { position: relative; display: flex; justify-content: space-between; align-items: flex-end; margin-top: -48rpx; }
.identity-row > view:first-child { padding-top: 70rpx; flex: 1; min-width: 0; }
.name { display: block; font-size: 40rpx; font-weight: 750; overflow-wrap: anywhere; }
.small { display: block; margin-top: 10rpx; color: #9b91a3; font-size: 23rpx; }
.avatar { flex-shrink: 0; width: 140rpx; height: 140rpx; border-radius: 28rpx; border: 8rpx solid white; background: #eee7f8; }
.avatar-placeholder { display: flex; justify-content: center; align-items: center; font-size: 50rpx; color: #7a55ac; }
.bio { display: block; padding-top: 24rpx; color: #7e7487; font-size: 26rpx; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.profile-actions { display: flex; gap: 14rpx; margin-top: 26rpx; }
.profile-actions button { flex: 1; margin: 0; padding: 0 16rpx; border-radius: 16rpx; background: #f4f1f8; color: #6d5589; font-size: 25rpx; }
.profile-actions .primary { background: #7851b2; color: white; }
.timeline { padding: 16rpx 36rpx; border-top: 14rpx solid #f8f6fb; }
.timeline-head { display: flex; justify-content: space-between; align-items: center; font-size: 30rpx; font-weight: 700; }
.timeline-head button { margin: 0; padding: 0; background: transparent; font-size: 23rpx; color: #907ca4; font-weight: 400; }
.visibility { display: block; margin: 10rpx 0 20rpx; font-size: 22rpx; line-height: 1.6; color: #a398ac; }
.moment { display: flex; gap: 24rpx; padding: 32rpx 0; border-bottom: 1rpx solid #f0edf4; }
.date { width: 110rpx; flex-shrink: 0; }
.date text { display: block; font-size: 20rpx; color: #9d92a6; }
.date text:first-child { color: #3c3148; font-size: 48rpx; font-weight: 750; line-height: 1.2; margin-bottom: 10rpx; }
.moment-main { min-width: 0; flex: 1; }
.moment-title { display: block; font-size: 29rpx; font-weight: 650; margin-bottom: 12rpx; }
.moment-body { display: block; font-size: 28rpx; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.images { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 8rpx; margin-top: 20rpx; }
.images image { width: 100%; height: 160rpx; border-radius: 8rpx; background: #f3f0f7; }
.images.single { grid-template-columns: 1fr; max-width: 400rpx; }
.images.single image { height: 400rpx; }
.moment-meta { display: flex; gap: 20rpx; margin-top: 20rpx; font-size: 22rpx; color: #a096a9; }
.moment-actions { display: flex; flex-wrap: wrap; gap: 16rpx; margin-top: 8rpx; }
.moment-actions button { margin: 0; padding: 0; background: transparent; color: #837197; font-size: 23rpx; }
.moment-actions .liked { color: #c25c8e; }.moment-actions .delete { color: #a99eaf; }
.state { display: flex; flex-direction: column; gap: 24rpx; align-items: center; padding: 80rpx 24rpx; color: #97899f; font-size: 27rpx; text-align: center; }
.state button,.load-more { background: #f5f1fa; color: #79569e; font-size: 26rpx; }
.end { display: block; padding: 40rpx 0; text-align: center; color: #b0a6b7; font-size: 22rpx; }
button::after { border: none; }
</style>
