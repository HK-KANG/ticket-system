<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="logo">
        <el-icon :size="20"><Service /></el-icon>
        <span>售后工单</span>
      </div>

      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.meta.icon" /></el-icon>
          <span>{{ item.meta.title }}</span>
        </el-menu-item>
      </el-menu>

      <div class="mock-tip" v-if="useMock">
        当前为演示数据<br />后端接口就绪后自动切换
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="crumb">{{ route.meta.title || '' }}</div>
        <div class="user">
          <el-tag size="small" type="info">{{ roleLabel }}</el-tag>
          <span class="name">{{ user.realName || user.userInfo?.username }}</span>
          <el-button link type="danger" @click="onLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ROLE_LABEL } from '@/utils/role'
import { USE_MOCK } from '@/api/mock'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const useMock = USE_MOCK

// 菜单按角色过滤：路由 meta.roles 里有当前角色才显示
const menus = computed(() => {
  const all = router.options.routes.find((r) => r.path === '/')?.children || []
  return all.filter((r) => !r.meta?.hidden && (!r.meta?.roles || r.meta.roles.includes(user.role)))
})

const roleLabel = computed(() => ROLE_LABEL[user.role] || '未登录')

async function onLogout() {
  await user.logout()
  router.push('/login')
}

// 进主界面时向服务端确认一次身份：localStorage 里的角色是用户能改的，不能当权限依据
onMounted(async () => {
  try {
    await user.refreshMe()
  } catch {
    // 令牌已失效，request.js 的 401 分支会跳回登录页，这里不用重复处理
  }
})
</script>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background: #ffffff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  font-weight: 600;
  color: #1f4e79;
  border-bottom: 1px solid #e4e7ed;
}

.menu {
  border-right: none;
  flex: 1;
}

.mock-tip {
  margin: 12px;
  padding: 8px 10px;
  background: #fdf6ec;
  color: #b88230;
  font-size: 12px;
  line-height: 1.6;
  border-radius: 4px;
}

.header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.crumb {
  font-size: 15px;
  font-weight: 600;
}

.user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.name {
  color: #606266;
}

.main {
  padding: 0;
  background: #f5f7fa;
  overflow-y: auto;
}
</style>
