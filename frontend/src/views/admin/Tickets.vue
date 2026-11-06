<template>
  <div class="page">
    <h2 class="page-title">工单总览</h2>
    <p class="page-desc">全量工单，六条件任意组合。管理员视角，不受技能组限制。</p>

    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="filters">
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in statuses" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="query.priority" placeholder="全部" clearable style="width: 100px">
            <el-option v-for="p in priorities" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理人">
          <el-select v-model="query.handlerId" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="a in agents" :key="a.id" :value="a.id" :label="a.realName" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker v-model="timeRange" type="datetimerange" range-separator="至"
                          start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD HH:mm:ss"
                          style="width: 360px" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="标题或单号" clearable style="width: 170px"
                    @keyup.enter="reload" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="ticketNo" label="单号" width="165" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="90" />
        <el-table-column label="优先级" width="85">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priority)" size="small">{{ row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.statusLabel }}</el-tag>
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
            <span :class="{ overdue: row.overdue }">{{ row.overdue ? '已超时' : remainingText(row.slaDeadline) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的工单" :image-size="80" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total"
                       :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
                       @current-change="load" @size-change="reload" />
      </div>
    </el-card>

    <el-drawer v-model="drawer" title="工单详情" size="600px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="单号">{{ detail.ticketNo }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="描述">{{ detail.content }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.statusLabel }}</el-descriptions-item>
          <el-descriptions-item label="客户 / 处理人">
            {{ detail.creatorName }} → {{ detail.handlerName || '未派单' }}
          </el-descriptions-item>
          <el-descriptions-item label="SLA 截止">{{ fmtSla(detail.slaDeadline) }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">流转记录</el-divider>
        <el-timeline v-if="detail.logs?.length">
          <el-timeline-item v-for="(l, i) in detail.logs" :key="i" :timestamp="fmtSla(l.createTime)" placement="top">
            <span class="action">{{ l.action }}</span>
            <span class="who">{{ l.operatorName }}</span>
            <div class="remark" v-if="l.remark">{{ l.remark }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无记录" :image-size="60" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { pageTickets, getTicket, listAgents } from '@/api/ticket'
import { CATEGORY_OPTIONS, STATUS_OPTIONS, PRIORITY_OPTIONS, statusType, priorityType, remainingText, fmtSla } from '@/utils/dict'

const categories = CATEGORY_OPTIONS
const statuses = STATUS_OPTIONS
const priorities = PRIORITY_OPTIONS
const agents = ref([])

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const timeRange = ref([])
const query = reactive({
  page: 1, size: 10, category: '', status: '', priority: '',
  handlerId: null, keyword: '', createTimeStart: null, createTimeEnd: null
})

const drawer = ref(false)
const detail = ref(null)

// 时间范围变化时拆成后端要的两个参数
watch(timeRange, (v) => {
  query.createTimeStart = v?.[0] || null
  query.createTimeEnd = v?.[1] || null
})

async function load() {
  loading.value = true
  try {
    const res = await pageTickets(query)
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
  Object.assign(query, {
    page: 1, size: 10, category: '', status: '', priority: '',
    handlerId: null, keyword: '', createTimeStart: null, createTimeEnd: null
  })
  timeRange.value = []
  load()
}

async function openDetail(row) {
  drawer.value = true
  detail.value = await getTicket(row.id)
}

onMounted(async () => {
  load()
  try {
    const all = await listAgents()
    agents.value = all.filter((u) => u.role === 'AGENT')
  } catch {
    agents.value = []
  }
})
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

.action {
  font-weight: 600;
  margin-right: 8px;
}

.who {
  color: #909399;
  font-size: 13px;
}

.remark {
  color: #606266;
  font-size: 13px;
  margin-top: 4px;
}
</style>
