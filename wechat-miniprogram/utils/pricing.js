const PLATFORM_SAFETY_FEE_RATE = 0.05

function formatCents(cents) {
  const yuan = cents / 100
  return Number.isInteger(yuan) ? String(yuan) : yuan.toFixed(2)
}

function hourlyBreakdown(value) {
  const input = String(value).trim()
  if (!/^\d+(?:\.\d{1,2})?$/.test(input)) return null
  const price = Number(input)
  if (!Number.isFinite(price) || price <= 0 || price > 999999) return null
  const grossCents = Math.round(price * 100)
  const feeCents = Math.round(grossCents * PLATFORM_SAFETY_FEE_RATE)
  return {
    gross: formatCents(grossCents),
    fee: formatCents(feeCents),
    net: formatCents(grossCents - feeCents)
  }
}

module.exports = { PLATFORM_SAFETY_FEE_RATE, hourlyBreakdown }
