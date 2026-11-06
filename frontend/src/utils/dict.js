// 字典与展示格式集中放这里，页面里不要再散落 if/else，改文案只改一处。

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

/** 状态下拉/标签页用，顺序按业务流程排 */
export const STATUS_OPTIONS = Object.keys(STATUS_LABEL).map((v) => ({ value: v, label: STATUS_LABEL[v] }))

/** 分类下拉。真实项目这里应来自后端分类字典接口，目前 SlaRule 里就是这几类 */
export const CATEGORY_OPTIONS = ['退款', '物流', '质量问题', '发票', '其他']

/** 优先级 */
export const PRIORITY_OPTIONS = ['P0', 'P1', 'P2', 'P3']

export const PRIORITY_LABEL = {
  P0: 'P0 紧急',
  P1: 'P1 高',
  P2: 'P2 中',
  P3: 'P3 低'
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
  // 后端返回 "yyyy-MM-dd HH:mm:ss"，部分浏览器对空格格式解析不稳定，统一替换成 ISO 里的 T
  const ms = new Date(String(deadline).replace(' ', 'T')).getTime() - Date.now()
  if (Number.isNaN(ms)) return '无 SLA'
  if (ms <= 0) return '已超时'
  const h = Math.floor(ms / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  const s = Math.floor((ms % 60000) / 1000)
  if (h > 0) return `${h} 小时 ${m} 分`
  if (m > 0) return `${m} 分 ${s} 秒`
  return `${s} 秒`
}

export function isOverdue(deadline) {
  if (!deadline) return false
  return new Date(String(deadline).replace(' ', 'T')).getTime() <= Date.now()
}

export function fmtSla(deadline) {
  if (!deadline) return '-'
  return String(deadline).slice(0, 16)
}
