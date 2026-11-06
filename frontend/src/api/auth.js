import service from './request'

/**
 * 登录。
 * backend: POST /api/auth/login  { username, password } -> { token, user }
 *
 * 认证从 Phase 2 起就是真接口了，没有 mock 分支：
 * 前端必须拿到后端签发的 JWT，否则拦截器会拦掉所有业务请求。
 */
export function login(form) {
  return service.post('/auth/login', form)
}

/** 登出：后端会删掉 Redis 里的令牌，旧 token 立即失效 */
export function logout() {
  return service.post('/auth/logout')
}

/** 当前登录用户：刷新页面后用它恢复身份，而不是长期信任 localStorage 里的角色 */
export function me() {
  return service.get('/auth/me')
}
