// 角色相关的公共逻辑，避免在多个页面里重复写 if (role === 'xxx')

export const ROLE = {
  CUSTOMER: 'CUSTOMER',
  AGENT: 'AGENT',
  ADMIN: 'ADMIN'
}

export const ROLE_LABEL = {
  CUSTOMER: '客户',
  AGENT: '坐席',
  ADMIN: '管理员'
}

/** 每种角色登录后的落地页 */
export function homePathOf(role) {
  switch (role) {
    case ROLE.CUSTOMER:
      return '/customer/tickets'
    case ROLE.AGENT:
      return '/agent/pool'
    case ROLE.ADMIN:
      return '/admin/tickets'
    default:
      return '/login'
  }
}
