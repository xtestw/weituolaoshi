<template>
<button class="card" @tap="open" :aria-label="&quot;查看&quot; + (item.title) + &quot;，&quot; + (item.price) + &quot;元每小时&quot;">
  <view :class="&quot;cover cover--&quot; + (item.tone)">
    <image v-if="item.coverUrl" class="cover__image" :src="item.coverUrl" mode="aspectFill"/>
    <view v-if="!item.coverUrl" class="cover__orb"></view>
    <view v-if="!item.coverUrl" class="cover__portrait"><view class="cover__head"></view><view class="cover__hair"></view><view class="cover__body"></view></view>
    <text v-if="item.available" class="cover__status">可约</text>
  </view>
  <view class="copy">
    <text class="title">{{ item.title }}</text>
    <view class="meta"><text class="meta__location">{{ item.coser }} · {{ item.city || item.district }}</text><text v-if="Number(item.rating) > 0" class="rating">★ {{ item.rating }}</text></view>
    <text class="availability">{{ item.availabilityLabel || (item.available ? '有开放档期 · 查看详情' : '档期待更新') }}</text>
    <view class="foot"><text class="price">¥{{ item.price }}<text class="price__unit">/小时</text></text><text v-if="item.minHours" class="minimum">{{ item.minHours }}小时起</text></view>
  </view>
</button>

</template>
<script>
export default {
props: {item: { type: Object, default: () => ({}) }},
methods: {
open() { this.$emit('open', { id: this.item.id }) },
setData(patch, callback) { Object.assign(this.$data, patch); if (callback) this.$nextTick(() => callback.call(this)) }
}
}
</script>
<style scoped>
.card { --color-text: #241d2d; --color-muted: #625b6b; --color-cta: #d64e87; --color-success: #16845b; --type-card: 30rpx; --type-caption: 23rpx; --weight-card: 700; width: 100%; margin: 0 0 20rpx; padding: 0; overflow: hidden; border: 1rpx solid rgba(231,226,236,.88); border-radius: 28rpx; background: #fff; color: var(--color-text); text-align: left; box-shadow: 0 4rpx 16rpx rgba(67,55,91,.035); }
.card:active { opacity: .82; }
.cover { position: relative; height: 0; padding-bottom: 133.333%; overflow: hidden; background: linear-gradient(145deg,#ede9fe,#e9d5ff); }
.cover__image { position: absolute; inset: 0; width: 100%; height: 100%; }
.cover--blue { background: linear-gradient(145deg,#ede9fe,#c4b5fd); }
.cover--pink { background: linear-gradient(145deg,#f3e8ff,#d8b4fe); }
.cover--violet { background: linear-gradient(145deg,#ece5ff,#c2b0f4); }
.cover--mint { background: linear-gradient(145deg,#dff7ee,#a6dfcb); }
.cover__orb { position: absolute; right: -32rpx; top: -28rpx; width: 190rpx; height: 190rpx; border-radius: 50%; background: rgba(255,255,255,.36); box-shadow: -190rpx 176rpx 0 34rpx rgba(255,255,255,.16); }
.cover__portrait { position: absolute; left: 50%; bottom: -20rpx; width: 210rpx; height: 244rpx; transform: translateX(-50%); }
.cover__head { position: absolute; z-index: 2; left: 61rpx; top: 44rpx; width: 88rpx; height: 100rpx; border-radius: 48% 48% 44% 44%; background: rgba(255,250,247,.92); }
.cover__hair { position: absolute; z-index: 3; left: 40rpx; top: 16rpx; width: 130rpx; height: 136rpx; border-radius: 52% 52% 34% 34%; border: 25rpx solid rgba(73,62,95,.68); border-bottom-width: 12rpx; box-sizing: border-box; }
.cover__body { position: absolute; left: 22rpx; bottom: 0; width: 166rpx; height: 118rpx; border-radius: 80rpx 80rpx 24rpx 24rpx; background: rgba(109,40,217,.62); box-shadow: inset 0 18rpx 0 rgba(255,255,255,.16); }
.cover__creator { position: absolute; z-index: 4; left: 18rpx; bottom: 16rpx; max-width: 220rpx; overflow: hidden; color: #fff; font-size: 22rpx; font-weight: 700; text-shadow: 0 2rpx 8rpx rgba(36,29,45,.36); white-space: nowrap; text-overflow: ellipsis; }
.cover__spark { position: absolute; z-index: 4; width: 18rpx; height: 18rpx; border-radius: 5rpx 50%; background: rgba(255,255,255,.9); transform: rotate(45deg); }
.cover__spark--one { left: 26rpx; top: 44rpx; }
.cover__spark--two { right: 30rpx; bottom: 62rpx; width: 12rpx; height: 12rpx; }
.cover__status { position: absolute; z-index: 4; right: 16rpx; top: 16rpx; padding: 7rpx 14rpx; border-radius: 999rpx; background: rgba(255,255,255,.9); color: var(--color-success); font-size: 22rpx; font-weight: 700; }
.copy { padding: 20rpx 18rpx 18rpx; }
.title { min-height: 76rpx; display: -webkit-box; overflow: hidden; color: var(--color-text); font-size: var(--type-card); font-weight: var(--weight-card); line-height: 1.3; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.meta,.foot { margin-top: 12rpx; display: flex; justify-content: space-between; align-items: center; gap: 10rpx; color: var(--color-muted); font-size: var(--type-caption); }
.meta__location { min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.rating { color: #8a6b18; white-space: nowrap; }
.price { color: var(--color-cta); font-size: 30rpx; font-weight: 800; }
.price__unit { font-size: 22rpx; font-weight: 500; }
.verified { padding: 5rpx 10rpx; border-radius: 999rpx; background: #eef8f3; color: var(--color-success); font-size: 22rpx; white-space: nowrap; }
.verified__check { font-weight: 900; }

.availability { display: block; margin-top: 14rpx; color: #80738e; font-size: 22rpx; line-height: 1.5; }
.minimum { color: #93879d; font-size: 22rpx; white-space: nowrap; }
.card::after { border: none; }
</style>
