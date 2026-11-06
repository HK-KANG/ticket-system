import service from './request'
import { USE_MOCK, MOCK_TICKETS, MOCK_POOL, mockPage } from './mock'

/**
 * 工单相关接口。
 * 现在全部走 mock；后端接口做好后，把每个函数里的 if (USE_MOCK) 分支删掉就是真接口。
 *
 * 后端契约（Phase 3/4 实现）：
 *   GET  /api/ticket/page       分页查询，按角色自动收窄
 *   GET  /api/ticket/pool       抢单池（仅 AGENT）
 *   POST /api/ticket/{id}/grab  抢单（乐观锁）
 *   GET  /api/ticket/{id}       详情
 *   POST /api/ticket/{id}/transfer  转派
 *   POST /api/ticket/{id}/close     关闭
 */

const delay = (ms) => new Promise((r) => setTimeout(r, ms))

/** 我的工单：分页 + 状态筛选 + 关键词 */
export async function pageMyTickets(params) {
  if (USE_MOCK) {
    await delay(300)
    let list = MOCK_TICKETS
    if (params.status) list = list.filter((t) => t.status === params.status)
    if (params.keyword) {
      const k = params.keyword.trim()
      list = list.filter((t) => t.title.includes(k) || t.ticketNo.includes(k))
    }
    return mockPage(list, params)
  }
  return service.get('/ticket/page', { params })
}

/** 工单总览：多条件 + 深分页 */
export async function pageAdminTickets(params) {
  if (USE_MOCK) {
    await delay(300)
    let list = MOCK_TICKETS
    if (params.status) list = list.filter((t) => t.status === params.status)
    if (params.priority) list = list.filter((t) => t.priority === params.priority)
    if (params.category) list = list.filter((t) => t.category === params.category)
    if (params.keyword) {
      const k = params.keyword.trim()
      list = list.filter((t) => t.title.includes(k) || t.ticketNo.includes(k))
    }
    return mockPage(list, params)
  }
  return service.get('/admin/ticket/page', { params })
}

/** 抢单池 */
export async function listPool() {
  if (USE_MOCK) {
    await delay(200)
    return MOCK_POOL
  }
  return service.get('/ticket/pool')
}

/** 抢单 */
export async function grabTicket(id) {
  if (USE_MOCK) {
    await delay(400)
    // 10% 概率失败，用来演示「已被他人抢走」的弹窗
    if (Math.random() < 0.1) {
      throw new Error('手慢了，这单已被其他坐席抢走')
    }
    return true
  }
  return service.post(`/ticket/${id}/grab`)
}

/** 工单详情 */
export async function getTicket(id) {
  if (USE_MOCK) {
    await delay(200)
    const hit = MOCK_TICKETS.find((t) => String(t.id) === String(id)) || MOCK_TICKETS[0]
    return hit
  }
  return service.get(`/ticket/${id}`)
}
