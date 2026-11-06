import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

/**
 * 统一请求封装。
 * 后端所有接口都返回 Result<T>{code,message,data,traceId}，
 * 这里在拦截器里拆掉外壳：业务代码拿到的直接是 data，不用每次 res.data.data。
 *
 * 另外两件事：
 *   1. 自动带 Authorization 头和 X-Trace-Id（和后端 TraceIdFilter 对接，出问题能凭 ID 查日志）
 *   2. code !== 0 统一弹错误提示，业务代码不用自己 try/catch 弹消息
 */
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 10000
})

service.interceptors.request.use(
  (config) => {
    const user = useUserStore()
    if (user.token) {
      config.headers.Authorization = `Bearer ${user.token}`
    }
    // 每次请求带一个 traceId，后端会原样写进日志和响应头
    config.headers['X-Trace-Id'] = Math.random().toString(16).slice(2, 18).padEnd(16, '0')
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response) => {
    const body = response.data
    // 非标准返回（比如文件流）直接透传
    if (body === null || typeof body !== 'object' || !('code' in body)) {
      return body
    }
    if (body.code === 0) {
      return body.data
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    const msg = error.response?.data?.message || error.message || '网络异常'

    // 401：令牌失效或被踢下线。清掉本地登录态并回登录页，
    // 不能只弹个提示让用户停在页面上——他接下来点什么都会失败。
    if (status === 401) {
      const user = useUserStore()
      user.clearLocal()
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }

    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service
