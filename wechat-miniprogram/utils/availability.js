const keyForDate = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`

const dayLabel = (key) => {
  const date = new Date(`${key}T12:00:00`)
  return `${date.getMonth() + 1}月${date.getDate()}日 周${'日一二三四五六'[date.getDay()]}`
}

const isBookableDay = (service, key) => {
  if (!key || !service || !service.available) return false
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const day = new Date(`${key}T00:00:00`)
  return !Number.isNaN(day.getTime()) && day >= today && (!Array.isArray(service.availableDates) || service.availableDates.includes(key))
}

const nextDays = (service, count = 14) => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Array.from({ length: count }, (_, offset) => {
    const day = new Date(today)
    day.setDate(today.getDate() + offset)
    const key = keyForDate(day)
    return { key, label: `${day.getMonth() + 1}/${day.getDate()}`, sub: `周${'日一二三四五六'[day.getDay()]}`, full: dayLabel(key), open: isBookableDay(service, key) }
  })
}

module.exports = { keyForDate, dayLabel, isBookableDay, nextDays }
