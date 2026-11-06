<template>
  <div class="page">
    <h2 class="page-title">导出中心</h2>
    <p class="page-desc">异步导出任务，进度和下载。后端用 EasyExcel + RabbitMQ 在 Phase 7 实现。</p>

    <el-card shadow="never">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="这页现在只有界面骨架"
        description="等 Phase 7 做完异步导出，这里会显示真实任务、进度条和下载链接。"
      />

      <el-table :data="rows" style="width: 100%; margin-top: 16px" empty-text="暂无导出任务">
        <el-table-column prop="name" label="任务名" min-width="200" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'DONE' ? 'success' : 'warning'">
              {{ row.status === 'DONE' ? '已完成' : '处理中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="220">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :status="row.progress === 100 ? 'success' : ''" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.progress < 100">下载</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px">
        <el-button type="primary" disabled>发起导出（Phase 7）</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
// 演示用两条，证明进度条样式可用
const rows = [
  { name: '2026-11 全部工单导出', createTime: '2026-11-06 10:12:00', status: 'DONE', progress: 100 },
  { name: '2026-11 退款类工单导出', createTime: '2026-11-06 14:35:00', status: 'RUNNING', progress: 62 }
]
</script>
