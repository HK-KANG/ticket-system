import service from './request'

/**
 * 异步导出接口。
 *
 * 流程：createExport 拿到任务号 → 轮询 listExports 看状态 →
 * SUCCESS 后调 downloadUrl 拼下载链接（带 token 用 axios 下载太重，直接拼 URL 交给浏览器）。
 */
export function createExport(params) {
  return service.post('/export/ticket', params)
}

export function listExports(limit = 10) {
  return service.get('/export/tasks', { params: { limit } })
}

/** 下载链接：浏览器直接打开会带不上 Authorization 头，所以用 request 实例下载 blob */
export function downloadExport(id) {
  return service.get(`/export/${id}/download`, { responseType: 'blob' })
}
