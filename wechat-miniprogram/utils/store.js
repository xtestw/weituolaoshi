const initial = {
  verification: null,
  coserVerification: { status: 'unverified', reason: '' },
  role: 'customer',
  favorites: [],
  draftPost: null,
  posts: [],
  orders: [
    {
      id: 'WT20261110001',
      title: '三月七｜漫展同行',
      coser: '阿梨',
      customer: '小鱼',
      date: '11月16日 14:00–17:00',
      place: '国家会展中心 2号门',
      amount: 564,
      status: 'paid_pending_confirm',
      statusText: '已付款，待Coser确认'
    }
  ]
}

function seedState() {
  if (!wx.getStorageSync('coser_v1_seeded')) {
    Object.keys(initial).forEach((key) => wx.setStorageSync(`coser_v1_${key}`, initial[key]))
    wx.setStorageSync('coser_v1_seeded', true)
  }
  if (!wx.getStorageSync('coser_v1_migration_2')) {
    const orders = get('orders', []).map((item) => item.id === 'WT20261110001' && item.amount === 592 ? { ...item, amount: 564 } : item)
    set('orders', orders)
    wx.setStorageSync('coser_v1_migration_2', true)
  }
}

function get(key, fallback) {
  const value = wx.getStorageSync(`coser_v1_${key}`)
  return value === '' ? fallback : value
}

function set(key, value) {
  wx.setStorageSync(`coser_v1_${key}`, value)
  return value
}

module.exports = { seedState, get, set }
