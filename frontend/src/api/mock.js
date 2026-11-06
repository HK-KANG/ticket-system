/**
 * 演示账号（登录页「一键填充」用）。
 *
 * 注意：Phase 3 起工单数据已全部接真后端，这里**不再有假数据**。
 * 保留这个文件只是因为登录页需要几个可点的示例账号，密码与后端 V2 初始化脚本一致。
 */

export const MOCK_USERS = [
  { username: 'zhangsan', password: '123456', role: 'CUSTOMER', realName: '张三（客户）' },
  { username: 'limei', password: '123456', role: 'CUSTOMER', realName: '李梅（客户）' },
  { username: 'lisi', password: '123456', role: 'AGENT', realName: '李四（坐席·售后一组）' },
  { username: 'zhaoliu', password: '123456', role: 'AGENT', realName: '赵六（坐席·售后二组）' },
  { username: 'admin', password: '123456', role: 'ADMIN', realName: '王五（管理员）' }
]

/** 工单数据已接真接口，这个开关现在只控制侧边栏提示是否显示 */
export const USE_MOCK = false
