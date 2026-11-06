<template>
  <div class="page">
    <h2 class="page-title">我的工单</h2>
    <p class="page-desc">只显示你提交的工单。点任意一行查看详情和流转记录。</p>

    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group v-model="query.status" @change="reload">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button v-for="s in statusOptions" :key="s.value" :value="s.value">
            {{ s.label }}
          </el-radio-button>
        </el-radio-group>

        <div class="right">
          <el-input v-model="query.keyword" placeholder="搜标题或单号" clearable style="width: 240px"
                    @keyup.enter="reload" @clear="reload">
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
            <el-tag :type="statusType(row.status)" size="small">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理人" width="100">
          <template #default="{ row }">{{ row.handlerName || '待派单' }}</template>
        </el-table-column>
        <el-table-column label="SLA" width="130">
          <template #default="{ row }">
            <span :class="{ overdue: row.overdue }">{{ row.overdue ? '已超时' : remainingText(row.slaDeadline) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="165" />
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
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total"
                       :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
                       @current-change="load" @size-change="reload" />
      </div>
    </el-card>

    <!-- 详情抽屉：一次请求拿回工单 + 流转日志 + 回复 -->
    <el-drawer v-model="drawer" title="工单详情" size="620px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="单号">{{ detail.ticketNo }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
            <el-descriptions-item label="描述">{{ detail.content }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ detail.category }}</el-descriptions-item>
            <el-descriptions-item label="优先级">{{ detail.priority }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusType(detail.status)" size="small">{{ detail.statusLabel }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="处理人">{{ detail.handlerName || '待派单' }}</el-descriptions-item>
            <el-descriptions-item label="SLA 截止">{{ fmtSla(detail.slaDeadline) }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ fmtSla(detail.createTime) }}</el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">流转记录</el-divider>
          <el-timeline v-if="detail.logs?.length">
            <el-timeline-item v-for="(l, i) in detail.logs" :key="i" :timestamp="fmtSla(l.createTime)" placement="top">
              <div class="log-line">
                <span class="action">{{ actionLabel(l.action) }}</span>
                <span class="who">{{ l.operatorName }}</span>
              </div>
              <div class="log-remark" v-if="l.remark">{{ l.remark }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无记录" :image-size="60" />

          <el-divider content-position="left">沟通记录</el-divider>
          <div v-if="detail.comments?.length" class="comments">
            <div v-for="c in detail.comments" :key="c.id" class="comment" :class="{ internal: c.isInternal === 1 }">
              <div class="head">
                <span class="who">{{ c.userName }}</span>
                <span class="time">{{ fmtSla(c.createTime) }}</span>
                <el-tag v-if="c.isInternal === 1" size="small" type="warning">内部</el-tag>
              </div>
              <div class="content">{{ c.content }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无回复" :image-size="60" />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { pageTickets, getTicket } from '@/api/ticket'
import { STATUS_OPTIONS, statusType, priorityType, remainingText, fmtSla } from '@/utils/dict'

const router = useRouter()
// 客户侧不显示"已升级"，那是坐席/管理员关心的状态
const statusOptions = STATUS_OPTIONS.filter((s) => s.value !== 'ESCALATED')

const ACTION_LABEL = {
  CREATE: '创建工单',
  ACCEPT: '接单',
  TRANSFER: '转派',
  RESOLVE: '标记解决',
  CLOSE: '关闭工单',
  ESCALATE: '超时升级'
}
const actionLabel = (a) => ACTION_LABEL[a] || a

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, status: '', keyword: '' })

const drawer = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

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

async function openDetail(row) {
  drawer.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getTicket(row.id)
  } finally {
    detailLoading.value = false
  }
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

.overdue {
  color: #f56c6c;
}

.log-line {
  display: flex;
  gap: 10px;
  align-items: center;
}

.action {
  font-weight: 600;
}

.who {
  color: #909399;
  font-size: 13px;
}

.log-remark {
  color: #606266;
  font-size: 13px;
  margin-top: 4px;
}

.comments {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.comment {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 10px 12px;
}

.comment.internal {
  background: #fdf6ec;
}

.head {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.time {
  color: #c0c4cc;
  font-size: 12px;
}

.content {
  white-space: pre-wrap;
}
</style>
