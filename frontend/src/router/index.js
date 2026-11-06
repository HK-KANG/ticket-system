import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { homePathOf } from '@/utils/role'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/customer/tickets',
    children: [
      {
        path: 'customer/submit',
        name: 'SubmitTicket',
        component: () => import('@/views/customer/Submit.vue'),
        meta: { title: '提交工单', icon: 'EditPen', roles: ['CUSTOMER'] }
      },
      {
        path: 'customer/tickets',
        name: 'MyTickets',
        component: () => import('@/views/customer/MyTickets.vue'),
        meta: { title: '我的工单', icon: 'Document', roles: ['CUSTOMER'] }
      },
      {
        path: 'agent/pool',
        name: 'AgentPool',
        component: () => import('@/views/agent/Pool.vue'),
        meta: { title: '抢单大厅', icon: 'Lightning', roles: ['AGENT', 'ADMIN'] }
      },
      {
        path: 'agent/ticket/:id',
        name: 'TicketDetail',
        component: () => import('@/views/agent/TicketDetail.vue'),
        meta: { title: '工单处理', icon: 'Tickets', roles: ['AGENT', 'ADMIN'], hidden: true }
      },
      {
        path: 'admin/tickets',
        name: 'AdminTickets',
        component: () => import('@/views/admin/Tickets.vue'),
        meta: { title: '工单总览', icon: 'DataLine', roles: ['ADMIN'] }
      },
      {
        path: 'admin/dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '数据看板', icon: 'TrendCharts', roles: ['ADMIN'] }
      },
      {
        path: 'admin/export',
        name: 'AdminExport',
        component: () => import('@/views/admin/Export.vue'),
        meta: { title: '导出中心', icon: 'Download', roles: ['ADMIN'] }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：没登录去登录页；角色不匹配回到自己的首页
router.beforeEach((to) => {
  const user = useUserStore()
  document.title = to.meta.title ? `${to.meta.title} · 售后工单系统` : '售后工单系统'

  if (to.path === '/login') {
    return true
  }
  if (!user.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles && !to.meta.roles.includes(user.role)) {
    return { path: homePathOf(user.role) }
  }
  return true
})

export default router
