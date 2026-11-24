const { request } = require('../services/api')
const { get, set } = require('./store')

const statusText = {
  awaiting_payment: '待支付', awaiting_coser: '已付款，待Coser确认',
  confirmed: '待履约', in_service: '履约中', completed: '已完成',
  refund_pending: '退款处理中', cancelled: '已取消'
}
const uiStatus = {
  awaiting_payment: 'unpaid', awaiting_coser: 'paid_pending_confirm',
  confirmed: 'service', in_service: 'in_service', completed: 'completed',
  refund_pending: 'cancel_pending', cancelled: 'cancelled'
}

function normalizeOrder(raw) {
  const previous = get('orders', []).find(item => item.id === raw.id) || {}
  const state = raw.status
  const start = String(raw.startAt || raw.start_at || '').replace('T', ' ').slice(0, 16)
  const end = String(raw.endAt || raw.end_at || '').replace('T', ' ').slice(11, 16)
  return { ...previous, id: raw.id, title: raw.title || raw.service_title_snapshot || previous.title,
    coser: raw.coser || raw.seller_name_snapshot || previous.coser,
    customer: raw.customer || previous.customer, customerId: raw.customerId || raw.customer_id,
    place: raw.place || previous.place, date: start ? `${start}–${end}` : previous.date,
    amount: Number(raw.payableAmount ?? raw.payable_amount ?? previous.amount * 100) / 100,
    status: uiStatus[state] || state, statusText: statusText[state] || state,
    checkInCode: raw.checkInCode || '' }
}

function saveOrder(raw) {
  const order = normalizeOrder(raw)
  set('orders', [order, ...get('orders', []).filter(item => item.id !== order.id)])
  return order
}

function loadOrders() {
  return request('/v1/orders').then(rows => {
    const orders = rows.map(normalizeOrder)
    set('orders', orders)
    return orders
  })
}

module.exports = { normalizeOrder, saveOrder, loadOrders }
