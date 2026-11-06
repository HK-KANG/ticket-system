<template>
  <div class="page">
    <h2 class="page-title">工单处理</h2>
    <p class="page-desc">状态流转、回复客户、内部备注。当前为演示数据，操作按钮在 Phase 3 接通。</p>

    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="head">
          <span class="no">{{ ticket?.ticketNo || '—' }}</span>
          <div class="tags">
            <el-tag v-if="ticket" :type="priorityType(ticket.priority)" size="small">{{ ticket.priority }}</el-tag>
            <el-tag v-if="ticket" :type="statusType(ticket.status)" size="small">{{ statusLabel(ticket.status) }}</el-tag>
          </div>
        </div>
      </template>

      <el-descriptions v-if="ticket" :column="2" border>
        <el-descriptions-item label="标题" :span="2">{{ ticket.title }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ ticket.category }}</el-descriptions-item>
        <el-descriptions-item label="技能组">{{ ticket.skillGroup }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ ticket.creatorName }}</el-descriptions-item>
        <el-descriptions-item label="处理人">{{ ticket.handlerName || '未派单' }}</el-descriptions-item>
        <el-descriptions-item label="SLA 截止">{{ fmtSla(ticket.slaDeadline) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ ticket.createTime }}</el-descriptions-item>
      </el-descriptions>

      <div class="actions">
        <el-tooltip content="Phase 3 实现：接单 / 解决 / 关闭 / 转派" placement="top">
          <span>
            <el-button type="primary" disabled>接单</el-button>
            <el-button disabled>标记解决</el-button>
            <el-button disabled>转派</el-button>
            <el-button type="danger" plain disabled>关闭</el-button>
          </span>
        </el-tooltip>
      </div>

      <el-divider content-position="left">回复区（Phase 3 实现）</el-divider>
      <el-input type="textarea" :rows="3" disabled placeholder="回复客户内容，以及仅坐席可见的内部备注" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getTicket } from '@/api/ticket'
import { statusLabel, statusType, priorityType, fmtSla } from '@/utils/dict'

const route = useRoute()
const loading = ref(false)
const ticket = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    ticket.value = await getTicket(route.params.id)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.no {
  font-family: Consolas, monospace;
  color: #606266;
}

.tags {
  display: flex;
  gap: 8px;
}

.actions {
  margin-top: 16px;
}
</style>
