<template>
  <div class="page">
    <h2 class="page-title">我的工单</h2>
    <p class="page-desc">这里只显示你提交的工单。点任意一行可以看详情。</p>

    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group v-model="query.status" @change="reload">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button v-for="s in statusOptions" :key="s.value" :value="s.value">
            {{ s.label }}
          </el-radio-button>
        </el-radio-group>

        <div class="right">
          <el-input
            v-model="query.keyword"
            placeholder="搜标题或单号"
            clearable
            style="width: 240px"
            @keyup.enter="reload"
            @clear="reload"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="goSubmit">提交工单</el-button>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" stripe style="width: 100%" @row-click="openDetail">
        <el-table-column prop="ticketNo" label="单号" width="170" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priority)" size="small">{{ row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="还没有工单，点右上角「提交工单」试试" :image-size="80" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="reload"
        />
      </div>
    </el-card>

    <!-- 详情抽屉：任务书要求「点开行看详情抽屉」 -->
    <el-drawer v-model="drawer" title="工单详情" size="520px">
      <el-descriptions v-if="current" :column="1" border>
        <el-descriptions-item label="单号">{{ current.ticketNo }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ current.title }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ current.category }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ current.priority }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(current.status) }}</el-descriptions-item>
        <el-descriptions-item label="处理人">{{ current.handlerName || '待派单' }}</el-descriptions-item>
        <el-descriptions-item label="SLA 截止">{{ fmtSla(current.slaDeadline) }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ current.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { pageMyTickets } from '@/api/ticket'
import { STATUS_OPTIONS } from '@/api/mock'
import { statusLabel, statusType, priorityType, fmtSla } from '@/utils/dict'

const router = useRouter()
const statusOptions = STATUS_OPTIONS.filter((s) => s.value !== 'ESCALATED')

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, status: '', keyword: '' })

const drawer = ref(false)
const current = ref(null)

async function load() {
  loading.value = true
  try {
    const res = await pageMyTickets(query)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function reload() {
  query.page = 1
  load()
}

function openDetail(row) {
  current.value = row
  drawer.value = true
}

function goSubmit() {
  router.push('/customer/submit')
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 12px;
  flex-wrap: wrap;
}

.right {
  display: flex;
  gap: 8px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
