const initial = {
  verification: null,
  coserVerification: { status: 'unverified', reason: '' },
  role: 'customer',
  favorites: [],
  draftPost: null,
  posts: [],
  orders: []
}

function seedState() {
  if (!uni.getStorageSync('coser_v1_seeded')) {
    Object.keys(initial).forEach((key) => uni.setStorageSync(`coser_v1_${key}`, initial[key]))
    uni.setStorageSync('coser_v1_seeded', true)
  }
  if (!uni.getStorageSync('coser_v1_migration_2')) {
    const orders = get('orders', []).map((item) => item.id === 'WT20261110001' && item.amount === 592 ? { ...item, amount: 564 } : item)
    set('orders', orders)
    uni.setStorageSync('coser_v1_migration_2', true)
  }
  if (!uni.getStorageSync('coser_v1_migration_3')) {
    set('orders', get('orders', []).filter((item) => item.id !== 'WT20261110001'))
    uni.removeStorageSync('bOrderAccepted')
    uni.setStorageSync('coser_v1_migration_3', true)
  }
}

function get(key, fallback) {
  const value = uni.getStorageSync(`coser_v1_${key}`)
  return value === '' ? fallback : value
}

function set(key, value) {
  uni.setStorageSync(`coser_v1_${key}`, value)
  return value
}

export { seedState, get, set }
