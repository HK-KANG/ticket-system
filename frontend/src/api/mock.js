/**
 * 前端假数据。
 * 后端接口还没做出来时，页面靠它渲染，保证界面能看、能点、能量数据。
 * 后端就绪后把 .env.development 里的 VITE_USE_MOCK 改成 false 即可，业务代码不用动。
 */

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

/** 演示账号：与后端 V2 初始化脚本里的 6 个账号一致，密码都是 123456。
 *  登录已经接真后端，这里只用于登录页的「一键填充」按钮。 */
export const MOCK_USERS = [
  { username: 'zhangsan', password: '123456', role: 'CUSTOMER', realName: '张三（客户）' },
  { username: 'limei', password: '123456', role: 'CUSTOMER', realName: '李梅（客户）' },
  { username: 'lisi', password: '123456', role: 'AGENT', realName: '李四（坐席·售后一组）' },
  { username: 'zhaoliu', password: '123456', role: 'AGENT', realName: '赵六（坐席·售后二组）' },
  { username: 'admin', password: '123456', role: 'ADMIN', realName: '王五（管理员）' }
]

const CATEGORIES = ['退款', '物流', '质量问题', '发票', '其他']
const STATUSES = [
  { value: 'PENDING', label: '待派单' },
  { value: 'ASSIGNED', label: '已派单' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'ESCALATED', label: '已升级' }
]
const PRIORITIES = ['P0', 'P1', 'P2', 'P3']

function pad(n) {
  return String(n).padStart(2, '0')
}

/** 生成一条工单 */
function makeTicket(i, overrides = {}) {
  const day = new Date()
  day.setDate(day.getDate() - (i % 7))
  const status = STATUSES[i % STATUSES.length].value
  return {
    id: i,
    ticketNo: `TK${day.getFullYear()}${pad(day.getMonth() + 1)}${pad(day.getDate())}${pad(i)}`,
    title: ['申请退款，商品未收到', '物流三天没更新', '收到的商品有划痕', '需要开具增值税发票', '优惠券无法使用', '客服态度问题投诉'][i % 6],
    category: CATEGORIES[i % CATEGORIES.length],
    priority: PRIORITIES[i % PRIORITIES.length],
    status,
    creatorName: ['张三', '李梅', '王强'][i % 3],
    handlerName: status === 'PENDING' ? '' : ['李四', '赵六'][i % 2],
    skillGroup: ['售后一组', '售后二组'][i % 2],
    slaDeadline: new Date(day.getTime() + (2 + (i % 6)) * 3600 * 1000).toISOString(),
    createTime: `${day.getFullYear()}-${pad(day.getMonth() + 1)}-${pad(day.getDate())} ${pad(9 + (i % 9))}:${pad((i * 7) % 60)}:00`,
    ...overrides
  }
}

/** 我的工单 / 工单总览 的列表数据 */
export const MOCK_TICKETS = Array.from({ length: 46 }, (_, idx) => makeTicket(idx + 1))

/** 抢单大厅：只有待派单的 */
export const MOCK_POOL = MOCK_TICKETS.filter((t) => t.status === 'PENDING').slice(0, 8)

export const CATEGORY_OPTIONS = CATEGORIES
export const STATUS_OPTIONS = STATUSES
export const PRIORITY_OPTIONS = PRIORITIES

export function mockLogin({ username, password }) {
  const hit = MOCK_USERS.find((u) => u.username === username && u.password === password)
  if (!hit) {
    return Promise.reject(new Error('用户名或密码错误（演示：customer / agent / admin，密码都是 123456）'))
  }
  return Promise.resolve({
    token: `mock-token-${hit.role.toLowerCase()}`,
    user: { username: hit.username, realName: hit.realName, role: hit.role }
  })
}

/** 内存分页，模拟后端分页返回 */
export function mockPage(list, { page = 1, size = 10 } = {}) {
  const start = (page - 1) * size
  return Promise.resolve({
    records: list.slice(start, start + size),
    total: list.length,
    page,
    size
  })
}
