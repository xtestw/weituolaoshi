const CITIES = ['上海', '北京', '成都', '广州', '深圳', '武汉', '杭州', '南京', '重庆', '天津', '郑州', '西安', '长沙']
const DISTRICTS = {
  上海: ['黄浦', '徐汇', '长宁', '静安', '普陀', '虹口', '杨浦', '浦东', '闵行', '宝山', '嘉定', '青浦', '松江'],
  北京: ['东城', '西城', '朝阳', '海淀', '丰台', '石景山', '通州', '昌平', '大兴'],
  成都: ['锦江', '青羊', '金牛', '武侯', '成华', '高新', '双流', '龙泉驿', '温江', '郫都', '新都'],
  广州: ['越秀', '荔湾', '海珠', '天河', '白云', '黄埔', '番禺'],
  深圳: ['福田', '罗湖', '南山', '盐田', '宝安', '龙岗', '龙华', '光明'],
  武汉: ['江岸', '江汉', '硚口', '汉阳', '武昌', '青山', '洪山', '东西湖'],
  杭州: ['上城', '拱墅', '西湖', '滨江', '萧山', '余杭', '钱塘', '临平'],
  南京: ['玄武', '秦淮', '建邺', '鼓楼', '栖霞', '雨花台', '江宁', '浦口'],
  重庆: ['渝中', '江北', '南岸', '沙坪坝', '九龙坡', '大渡口', '渝北', '巴南', '北碚'],
  天津: ['和平', '河东', '河西', '南开', '河北', '红桥', '滨海', '东丽', '西青', '津南', '北辰'],
  郑州: ['中原', '二七', '金水', '管城', '惠济', '郑东', '高新', '经开'],
  西安: ['新城', '碑林', '莲湖', '雁塔', '未央', '灞桥', '长安', '高新', '曲江'],
  长沙: ['芙蓉', '天心', '岳麓', '开福', '雨花', '望城']
}
const CENTERS = {
  上海: [31.2304, 121.4737], 北京: [39.9042, 116.4074], 成都: [30.5728, 104.0668], 广州: [23.1291, 113.2644],
  深圳: [22.5431, 114.0579], 武汉: [30.5928, 114.3055], 杭州: [30.2741, 120.1551], 南京: [32.0603, 118.7969],
  重庆: [29.5630, 106.5516], 天津: [39.3434, 117.3616], 郑州: [34.7466, 113.6254], 西安: [34.3416, 108.9398], 长沙: [28.2282, 112.9388]
}
function distanceKm(lat1, lon1, lat2, lon2) {
  const rad = Math.PI / 180
  const dLat = (lat2 - lat1) * rad
  const dLon = (lon2 - lon1) * rad
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) ** 2
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}
function cityFromCoordinates(latitude, longitude) {
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return ''
  const nearest = CITIES.map((city) => ({ city, distance: distanceKm(latitude, longitude, ...CENTERS[city]) })).sort((a, b) => a.distance - b.distance)[0]
  return nearest.distance <= 120 ? nearest.city : ''
}
function validateVenue(location, expectedCity) {
  const latitude = Number(location.latitude)
  const longitude = Number(location.longitude)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return { valid: false, reason: '地点坐标无效，请重新选择' }
  const address = `${location.address || ''} ${location.name || ''} ${location.city || ''} ${location.district || ''} ${location.place || ''}`
  const city = CITIES.filter((item) => address.includes(item)).sort((a, b) => address.indexOf(a) - address.indexOf(b))[0] || cityFromCoordinates(latitude, longitude)
  if (!city || city !== expectedCity) return { valid: false, reason: `仅支持${expectedCity}城区的交易地点` }
  const district = (DISTRICTS[city] || []).find((item) => address.includes(item))
  if (!district) return { valid: false, reason: '请选择开放城区内的公共场所' }
  const [centerLat, centerLon] = CENTERS[city]
  if (distanceKm(latitude, longitude, centerLat, centerLon) > 55) return { valid: false, reason: '地点超出开放城区范围' }
  if (/酒店|宾馆|民宿|公寓|住宅|小区|家中|私人会所|夜店|酒吧/.test(address)) return { valid: false, reason: '请选择公开营业的公共场所' }
  return { valid: true, city, district, place: location.name || location.address, latitude: location.latitude, longitude: location.longitude }
}
module.exports = { CITIES, DISTRICTS, cityFromCoordinates, validateVenue }
