import { request } from '../services/api'

const services = [
  { id: 'svc-1', title: '三月七｜漫展同行与轻剧情互动', coser: '阿梨', gender: '女', district: '青浦', rating: 4.9, price: 188, credit: 'excellent', available: true, mark: 'MAR', tone: 'blue' },
  { id: 'svc-2', title: '芙宁娜｜主题摄影协作', coser: '小北', gender: '女', district: '徐汇', rating: 4.8, price: 228, credit: 'excellent', available: true, mark: 'FUR', tone: 'violet' },
  { id: 'svc-3', title: '自选角色｜生日互动与城市同行', coser: '布丁', gender: '女', district: '浦东', rating: 4.7, price: 268, credit: 'good', available: false, mark: 'COS', tone: 'pink' },
  { id: 'svc-4', title: '乙游角色｜主题活动同行', coser: '弦月', gender: '女', district: '静安', rating: 4.9, price: 198, credit: 'excellent', available: true, mark: 'OTM', tone: 'mint' },
  { id: 'svc-5', title: '原神角色｜咖啡店主题互动', coser: '遥青', gender: '女', district: '黄浦', rating: 4.6, price: 168, credit: 'good', available: true, mark: 'GEN', tone: 'violet' }
]

let liveCatalog = false
function mapService(item) {
  return {
    id: item.id,
    title: item.title,
    coser: item.coser,
    city: item.city,
    district: item.city,
    price: Number(item.pricePerHour) / 100,
    minHours: item.minHours,
    maxHours: item.maxHours,
    available: !!item.available,
    verified: !!item.verified,
    coverUrl: item.coverUrl ? (item.coverUrl.startsWith('/') ? `${getApp().globalData.apiBaseUrl}${item.coverUrl}` : item.coverUrl) : '',
    ipName: item.ipName || '',
    characterName: item.characterName || '',
    gender: '',
    rating: null,
    mark: (item.characterName || item.title || 'COS').slice(0, 3),
    tone: 'violet'
  }
}
function loadCatalog() {
  return request('/v1/services').then(rows => {
    services.splice(0, services.length, ...rows.map(mapService))
    liveCatalog = true
    return services
  })
}
function isLiveCatalog() { return liveCatalog }
export { services, loadCatalog, mapService, isLiveCatalog }
