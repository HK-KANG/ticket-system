<template>
  <div class="page">
    <div class="head">
      <div>
        <h2 class="page-title">抢单大厅</h2>
        <p class="page-desc">
          本组待抢 {{ list.length }} 单 · 每 30 秒自动刷新 · 最后刷新 {{ lastRefresh }}
        </p>
      </div>
      <el-button :icon="Refresh" @click="load">立即刷新</el-button>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px"
              title="抢单功能在 Phase 4 上线"
              description="现在这里展示的是真实的待派单工单（按你的技能组过滤），抢单按钮暂不可用——那需要乐观锁和并发测试，单独一个阶段做。" />

    <el-empty v-if="!loading && list.length === 0" description="本组暂无待派单工单" />

    <div v-else class="grid" v-loading="loading">
      <el-card v-for="t in list" :key="t.id" shadow="hover" class="card" @click="openDetail(t)">
        <div class="card-top">
          <span class="no">{{ t.ticketNo }}</span>
          <el-tag :type="priorityType(t.priority)" size="small" effect="dark">{{ t.priority }}</el-tag>
        </div>

        <h3 class="title" :title="t.title">{{ t.title }}</h3>

        <div class="meta">
          <el-tag size="small" type="info">{{ t.category }}</el-tag>
          <span class="group">{{ t.skillGroup }}</span>
        </div>

        <div class="sla" :class="{ overdue: t.overdue }">
          <el-icon><Timer /></el-icon>
          <span>{{ t.overdue ? '已超时' : 'SLA 剩余 ' + remainingText(t.slaDeadline) }}</span>
        </div>

        <div class="foot">
          <span class="time">{{ fmtSla(t.createTime) }}</span>
          <el-tooltip content="Phase 4 实现（乐观锁抢单）" placement="top">
            <span><el-button type="primary" size="small" disabled>抢单</el-button></span>
          </el-tooltip>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Timer } from '@element-plus/icons-vue'
import { pageTickets } from '@/api/ticket'
import { priorityType, remainingText, fmtSla } from '@/utils/dict'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const lastRefresh = ref('-')

let timer = null
let ticker = null

async function load() {
  loading.value = true
  try {
    // 后端按角色收窄：坐席只会拿到本组 + 待派单的单子
    const res = await pageTickets({ status: 'PENDING', page: 1, size: 50 })
    list.value = res.records
    lastRefresh.value = new Date().toLocaleTimeString('zh-CN')
  } finally {
    loading.value = false
  }
}

function openDetail(row) {
  router.push(`/agent/ticket/${row.id}`)
}

onMounted(() => {
  load()
  timer = setInterval(load, 30000)
  // 每秒重算一次倒计时，让 SLA 在界面上真的在走
  ticker = setInterval(() => {
    list.value = [...list.value]
  }, 1000)
})

onUnmounted(() => {
  clearInterval(timer)
  clearInterval(ticker)
})
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}

.card {
  cursor: pointer;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.no {
  font-family: Consolas, monospace;
  font-size: 12px;
  color: #909399;
}

.title {
  font-size: 15px;
  margin: 10px 0;
  height: 44px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}

.group {
  font-size: 12px;
  color: #909399;
}

.sla {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #67c23a;
  background: #f0f9eb;
  padding: 6px 10px;
  border-radius: 4px;
}

.sla.overdue {
  color: #f56c6c;
  background: #fef0f0;
}

.foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.time {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
