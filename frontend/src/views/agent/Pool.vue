<template>
  <div class="page">
    <div class="head">
      <div>
        <h2 class="page-title">抢单大厅</h2>
        <p class="page-desc">
          池中 {{ list.length }} 单待抢 · 每 30 秒自动刷新 · 最后刷新 {{ lastRefresh }}
        </p>
      </div>
      <el-button :icon="Refresh" @click="load">立即刷新</el-button>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="池子空了，等新工单进来" />

    <div v-else class="grid" v-loading="loading">
      <el-card v-for="t in list" :key="t.id" shadow="hover" class="card">
        <div class="card-top">
          <span class="no">{{ t.ticketNo }}</span>
          <el-tag :type="priorityType(t.priority)" size="small" effect="dark">{{ t.priority }}</el-tag>
        </div>

        <h3 class="title" :title="t.title">{{ t.title }}</h3>

        <div class="meta">
          <el-tag size="small" type="info">{{ t.category }}</el-tag>
          <span class="group">{{ t.skillGroup }}</span>
        </div>

        <div class="sla" :class="{ overdue: isOverdue(t.slaDeadline) }">
          <el-icon><Timer /></el-icon>
          <span>{{ isOverdue(t.slaDeadline) ? '已超时' : 'SLA 剩余 ' + remainingText(t.slaDeadline) }}</span>
        </div>

        <div class="foot">
          <span class="time">{{ t.createTime }}</span>
          <el-button type="primary" size="small" :loading="grabbing === t.id" @click="onGrab(t)">
            抢单
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Timer } from '@element-plus/icons-vue'
import { listPool, grabTicket } from '@/api/ticket'
import { priorityType, remainingText, isOverdue } from '@/utils/dict'

const list = ref([])
const loading = ref(false)
const grabbing = ref(null)
const lastRefresh = ref('-')

let timer = null
let ticker = null

async function load() {
  loading.value = true
  try {
    list.value = await listPool()
    lastRefresh.value = new Date().toLocaleTimeString('zh-CN')
  } finally {
    loading.value = false
  }
}

async function onGrab(ticket) {
  try {
    await ElMessageBox.confirm(`确认接单：${ticket.title}？`, '抢单确认', {
      confirmButtonText: '确认接单',
      cancelButtonText: '再看看',
      type: 'warning'
    })
  } catch {
    return
  }

  grabbing.value = ticket.id
  try {
    await grabTicket(ticket.id)
    ElMessage.success('抢单成功，已进入你的处理列表')
    list.value = list.value.filter((x) => x.id !== ticket.id)
  } catch (e) {
    // D3：抢单失败提示 —— 明确告诉用户「被别人抢走了」，并立刻刷新列表
    ElMessageBox.alert(e.message || '抢单失败', '手慢了', {
      confirmButtonText: '刷新看看',
      type: 'error'
    }).finally(load)
  } finally {
    grabbing.value = null
  }
}

onMounted(() => {
  load()
  // 30 秒自动刷新（任务书要求）
  timer = setInterval(load, 30000)
  // 每秒刷新一次倒计时，让 SLA 在界面上真的在走
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
