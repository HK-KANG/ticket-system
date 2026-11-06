import service from './request'

/**
 * 工单接口。全部直连后端（Phase 3 起没有 mock 分支）。
 *
 * 后端契约：
 *   POST /api/ticket/create                创建（requestId 幂等）
 *   GET  /api/ticket/page                  分页，六条件
 *   GET  /api/ticket/{id}                  详情（含日志与回复）
 *   POST /api/ticket/{id}/comment          回复
 *   POST /api/ticket/{id}/transfer         转派
 *   POST /api/ticket/{id}/close            关闭
 *   GET  /api/ticket/stats                 统计（仅 ADMIN）
 */

/**
 * 创建工单。
 * requestId 由调用方生成（同一份表单重复提交必须用同一个），后端据此做幂等。
 */
export function createTicket(data) {
  return service.post('/ticket/create', data)
}

/** 分页查询。参数：page/size/category/status/priority/handlerId/createTimeStart/createTimeEnd/keyword */
export function pageTickets(params) {
  return service.get('/ticket/page', { params })
}

/** 工单详情（含流转日志、回复列表） */
export function getTicket(id) {
  return service.get(`/ticket/${id}`)
}

/** 回复工单，internal=true 为内部备注（仅坐席可见） */
export function commentTicket(id, data) {
  return service.post(`/ticket/${id}/comment`, data)
}

/** 转派给指定坐席 */
export function transferTicket(id, data) {
  return service.post(`/ticket/${id}/transfer`, data)
}

/** 关闭工单 */
export function closeTicket(id, data) {
  return service.post(`/ticket/${id}/close`, data)
}

/** 统计看板数据（仅管理员可调） */
export function getStats() {
  return service.get('/ticket/stats')
}

/** 坐席列表，转派弹窗选人用（坐席和管理员都能调） */
export function listAgents() {
  return service.get('/user/agents')
}
