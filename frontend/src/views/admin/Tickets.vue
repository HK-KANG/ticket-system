<template>
  <div class="page">
    <h2 class="page-title">工单总览</h2>
    <p class="page-desc">全量工单，多条件查询。管理员视角，不分技能组。</p>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="filters">
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="s in statuses" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="query.priority" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="p in priorities" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="标题或单号" clearable style="width: 180px" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="ticketNo" label="单号" width="165" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="90" />
        <el-table-column label="优先级" width="85">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priority)" size="small">{{ row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="客户" width="90" />
        <el-table-column label="处理人" width="90">
          <template #default="{ row }">{{ row.handlerName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="skillGroup" label="技能组" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="165" />
        <el-table-column label="SLA" width="120">
          <template #default="{ row }">
            <span :class="isOverdue(row.slaDeadline) ? 'overdue' : ''">{{ remainingText(row.slaDeadline) }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的工单" :image-size="80" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="reload"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { pageAdminTickets } from '@/api/ticket'
import { CATEGORY_OPTIONS, STATUS_OPTIONS, PRIORITY_OPTIONS } from '@/api/mock'
import { statusLabel, statusType, priorityType, remainingText, isOverdue } from '@/utils/dict'

const categories = CATEGORY_OPTIONS
const statuses = STATUS_OPTIONS
const priorities = PRIORITY_OPTIONS

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, category: '', status: '', priority: '', keyword: '' })

async function load() {
  loading.value = true
  try {
    const res = await pageAdminTickets(query)
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

function reset() {
  Object.assign(query, { page: 1, size: 10, category: '', status: '', priority: '', keyword: '' })
  load()
}

onMounted(load)
</script>

<style scoped>
.filters {
  margin-bottom: 4px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.overdue {
  color: #f56c6c;
}
</style>
