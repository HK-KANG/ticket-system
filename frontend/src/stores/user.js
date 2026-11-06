import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, me as meApi } from '@/api/auth'

const TOKEN_KEY = 'ticket_token'
const USER_KEY = 'ticket_user'

/**
 * 登录态。
 *
 * token 与用户信息存在 localStorage，只为「刷新页面不用重新登录」。
 * 注意角色以服务端返回为准：页面刷新后会调 /auth/me 再确认一次，
 * 因为 localStorage 是用户能随手改的，不能拿它当权限依据。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),

  getters: {
    role: (state) => state.userInfo?.role || '',
    realName: (state) => state.userInfo?.realName || '',
    isLogin: (state) => !!state.token
  },

  actions: {
    async login(form) {
      const data = await loginApi(form)
      this.token = data.token
      this.userInfo = data.user
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.userInfo))
      return data
    },

    /** 用服务端的身份覆盖本地缓存，防止本地被篡改 */
    async refreshMe() {
      if (!this.token) return null
      const user = await meApi()
      this.userInfo = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
      return user
    },

    /** 只清本地，不发请求（401 时用，避免又一次失败请求） */
    clearLocal() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },

    /** 正常登出：通知后端撤销令牌，再清本地 */
    async logout() {
      try {
        await logoutApi()
      } catch {
        // 令牌可能已过期，登出失败不影响本地清理
      }
      this.clearLocal()
    }
  }
})
