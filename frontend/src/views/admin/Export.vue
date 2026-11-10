<template>
  <div class="page">
    <h2 class="page-title">导出中心</h2>
    <p class="page-desc">
      导出是异步的：点「发起导出」只创建任务，文件在后台生成，好了这里会变成「已完成」并可下载。
      单次最多 5 万行。
    </p>

    <el-card shadow="never" style="margin-bottom: 16px">
      <el-form :inline="true" :model="form" label-width="72px">
        <el-form-item label="分类">
          <el-select v-model="form.category" clearable placeholder="全部" style="width: 130px">
            <el-option v-for="c in CATEGORY_OPTIONS" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" clearable placeholder="全部" style="width: 100px">
            <el-option v-for="p in PRIORITY_OPTIONS" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="form.keyword" placeholder="标题或工单号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="creating" @click="onCreate">发起导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="head">
        <span>我的导出记录</span>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-table :data="tasks" v-loading="loading" empty-text="还没有导出记录">
        <el-table-column prop="taskNo" label="任务号" width="200" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rowCount" label="行数" width="100">
          <template #default="{ row }">{{ row.rowCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="文件大小" width="110">
          <template #default="{ row }">{{ sizeText(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="发起时间" width="180" />
        <el-table-column label="操作" min-width="120">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUCCESS'" type="primary" link @click="onDownload(row)">
              下载
            </el-button>
            <span v-else-if="row.status === 'FAILED'" class="err">{{ row.errorMsg }}</span>
            <span v-else class="wait">生成中…</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { createExport, listExports, downloadExport } from '@/api/export'
import { CATEGORY_OPTIONS, STATUS_OPTIONS, PRIORITY_OPTIONS } from '@/utils/dict'

const form = ref({ category: '', status: '', priority: '', keyword: '' })
const tasks = ref([])
const loading = ref(false)
const creating = ref(false)
let timer = null

const statusTag = (s) => ({ SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', PENDING: 'info' }[s] || 'info')
const sizeText = (b) => (b == null ? '-' : b > 1048576 ? (b / 1048576).toFixed(1) + ' MB' : Math.round(b / 1024) + ' KB')

async function load() {
  loading.value = true
  try {
    const list = await listExports(10)
    tasks.value = list
    // 有未完成的任务就继续轮询，全部结束就停——不要无脑一直请求
    const pending = list.some((t) => t.status === 'PENDING' || t.status === 'RUNNING')
    if (!pending && timer) {
      clearInterval(timer)
      timer = null
    }
  } finally {
    loading.value = false
  }
}

function startPolling() {
  if (timer) return
  timer = setInterval(load, 2000)
}

async function onCreate() {
  creating.value = true
  try {
    const task = await createExport(form.value)
    ElMessage.success(`任务已创建：${task.taskNo}`)
    await load()
    startPolling()
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function onDownload(row) {
  try {
    const blob = await downloadExport(row.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName || 'tickets.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败，文件可能已过期')
  }
}

onMounted(load)
onUnmounted(() => timer && clearInterval(timer))
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.err {
  color: #f56c6c;
  font-size: 12px;
}
.wait {
  color: #909399;
  font-size: 12px;
}
</style>
