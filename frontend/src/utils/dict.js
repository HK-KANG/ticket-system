// 字典与展示格式：状态、优先级的中文名和标签颜色集中放这里，
// 页面里不要再散落 if/else，改文案只改一处。

export const STATUS_LABEL = {
  PENDING: '待派单',
  ASSIGNED: '已派单',
  PROCESSING: '处理中',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
  ESCALATED: '已升级'
}

const STATUS_TAG = {
  PENDING: 'warning',
  ASSIGNED: 'primary',
  PROCESSING: 'primary',
  RESOLVED: 'success',
  CLOSED: 'info',
  ESCALATED: 'danger'
}

const PRIORITY_TAG = {
  P0: 'danger',
  P1: 'warning',
  P2: 'primary',
  P3: 'info'
}

export function statusLabel(code) {
  return STATUS_LABEL[code] || code || '-'
}

export function statusType(code) {
  return STATUS_TAG[code] || 'info'
}

export function priorityType(code) {
  return PRIORITY_TAG[code] || 'info'
}

/** 剩余时间文案：抢单卡片和详情页都用它 */
export function remainingText(deadline) {
  if (!deadline) return '无 SLA'
  const ms = new Date(deadline).getTime() - Date.now()
  if (ms <= 0) return '已超时'
  const h = Math.floor(ms / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  const s = Math.floor((ms % 60000) / 1000)
  if (h > 0) return `${h} 小时 ${m} 分`
  if (m > 0) return `${m} 分 ${s} 秒`
  return `${s} 秒`
}

export function isOverdue(deadline) {
  return deadline ? new Date(deadline).getTime() <= Date.now() : false
}

export function fmtSla(deadline) {
  if (!deadline) return '-'
  const d = new Date(deadline)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
