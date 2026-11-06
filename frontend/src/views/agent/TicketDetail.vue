<template>
  <div class="page" v-loading="loading">
    <div class="head">
      <div>
        <h2 class="page-title">工单处理</h2>
        <p class="page-desc" v-if="ticket">{{ ticket.ticketNo }} · 创建于 {{ fmtSla(ticket.createTime) }}</p>
      </div>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <template v-if="ticket">
      <el-row :gutter="14">
        <el-col :span="15">
          <el-card shadow="never">
            <template #header>
              <div class="card-head">
                <span class="title">{{ ticket.title }}</span>
                <div class="tags">
                  <el-tag :type="priorityType(ticket.priority)" size="small">{{ ticket.priority }}</el-tag>
                  <el-tag :type="statusType(ticket.status)" size="small">{{ ticket.statusLabel }}</el-tag>
                </div>
              </div>
            </template>

            <el-descriptions :column="2" border>
              <el-descriptions-item label="分类">{{ ticket.category }}</el-descriptions-item>
              <el-descriptions-item label="技能组">{{ ticket.skillGroup }}</el-descriptions-item>
              <el-descriptions-item label="客户">{{ ticket.creatorName }}</el-descriptions-item>
              <el-descriptions-item label="处理人">{{ ticket.handlerName || '未派单' }}</el-descriptions-item>
              <el-descriptions-item label="SLA 截止">
                <span :class="{ overdue: ticket.overdue }">
                  {{ fmtSla(ticket.slaDeadline) }}（{{ ticket.overdue ? '已超时' : remainingText(ticket.slaDeadline) }}）
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="优先级">{{ ticket.priority }}</el-descriptions-item>
            </el-descriptions>

            <div class="block">
              <div class="block-title">问题描述</div>
              <div class="content">{{ ticket.content }}</div>
            </div>

            <div class="actions">
              <el-button v-if="ticket.status === 'PENDING'" type="primary" :loading="accepting"
                         @click="doAccept">抢单</el-button>
              <el-button v-if="canResolve" type="success" @click="resolveVisible = true">标记解决</el-button>
              <el-button type="primary" plain :disabled="ticket.status === 'CLOSED'"
                         @click="openTransfer">转派</el-button>
              <el-button type="danger" plain :disabled="ticket.status === 'CLOSED'"
                         @click="openClose">关闭工单</el-button>
            </div>
          </el-card>

          <el-card shadow="never" class="mt">
            <template #header>沟通记录</template>
            <div v-if="ticket.comments?.length" class="comments">
              <div v-for="c in ticket.comments" :key="c.id" class="comment" :class="{ internal: c.isInternal === 1 }">
                <div class="c-head">
                  <span class="who">{{ c.userName }}</span>
                  <span class="time">{{ fmtSla(c.createTime) }}</span>
                  <el-tag v-if="c.isInternal === 1" size="small" type="warning">内部备注</el-tag>
                </div>
                <div class="c-body">{{ c.content }}</div>
              </div>
            </div>
            <el-empty v-else description="还没有回复" :image-size="60" />

            <div class="reply">
              <el-input v-model="reply" type="textarea" :rows="3" maxlength="1000" show-word-limit
                        placeholder="回复内容…" :disabled="ticket.status === 'CLOSED'" />
              <div class="reply-foot">
                <el-checkbox v-model="internal">仅坐席可见（内部备注）</el-checkbox>
                <el-button type="primary" :loading="replying"
                           :disabled="ticket.status === 'CLOSED' || !reply.trim()"
                           @click="submitReply">发送</el-button>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="9">
          <el-card shadow="never">
            <template #header>流转记录</template>
            <el-timeline v-if="ticket.logs?.length">
              <el-timeline-item v-for="(l, i) in ticket.logs" :key="i" :timestamp="fmtSla(l.createTime)" placement="top">
                <div class="log-line">
                  <span class="action">{{ actionLabel(l.action) }}</span>
                  <span class="who">{{ l.operatorName }}</span>
                </div>
                <div class="log-remark" v-if="l.remark">{{ l.remark }}</div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无记录" :image-size="60" />
          </el-card>
        </el-col>
      </el-row>
    </template>

    <!-- D1 转派弹窗 -->
    <el-dialog v-model="transferVisible" title="转派工单" width="460px">
      <el-form label-width="80px">
        <el-form-item label="目标坐席">
          <el-select v-model="transferForm.toHandlerId" placeholder="选择坐席" style="width: 100%">
            <el-option v-for="a in agents" :key="a.id" :value="a.id"
                       :label="`${a.realName}（${a.skillGroup || '未分组'}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="转派原因">
          <el-input v-model="transferForm.reason" maxlength="20" placeholder="如：不在我的技能组" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferring" @click="doTransfer">确认转派</el-button>
      </template>
    </el-dialog>

    <!-- 标记解决弹窗 -->
    <el-dialog v-model="resolveVisible" title="标记已解决" width="460px">
      <el-form label-width="80px">
        <el-form-item label="处理结果">
          <el-input v-model="resolveForm.resolution" type="textarea" :rows="3" maxlength="500"
                    placeholder="写清怎么解决的，会记进流转日志" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="success" :loading="resolving" @click="doResolve">确认已解决</el-button>
      </template>
    </el-dialog>

    <!-- D2 关闭弹窗 -->
    <el-dialog v-model="closeVisible" title="关闭工单" width="460px">
      <el-form label-width="80px">
        <el-form-item label="处理结果">
          <el-input v-model="closeForm.resolution" type="textarea" :rows="3" maxlength="500"
                    placeholder="写清怎么解决的，会记进流转日志" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="danger" :loading="closing" @click="doClose">确认关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTicket, commentTicket, transferTicket, closeTicket, listAgents, acceptTicket, resolveTicket } from '@/api/ticket'
import { useUserStore } from '@/stores/user'
import { statusType, priorityType, remainingText, fmtSla } from '@/utils/dict'

const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const ticket = ref(null)

const reply = ref('')
const internal = ref(false)
const replying = ref(false)

const accepting = ref(false)
const resolveVisible = ref(false)
const resolveForm = reactive({ resolution: '' })
const resolving = ref(false)

const transferVisible = ref(false)
const transferForm = reactive({ toHandlerId: null, reason: '' })
const transferring = ref(false)
const agents = ref([])

const closeVisible = ref(false)
const closeForm = reactive({ resolution: '' })
const closing = ref(false)

const ACTION_LABEL = {
  CREATE: '创建工单',
  ACCEPT: '接单',
  TRANSFER: '转派',
  RESOLVE: '标记解决',
  CLOSE: '关闭工单',
  ESCALATE: '超时升级'
}
const actionLabel = (a) => ACTION_LABEL[a] || a

/** 只有处理中的工单、且是本人或管理员，才显示「标记解决」 */
const canResolve = computed(() => {
  if (!ticket.value) return false
  const st = ticket.value.status
  if (st !== 'PROCESSING' && st !== 'ESCALATED') return false
  const isAdmin = userStore.role === 'ADMIN'
  const isHandler = ticket.value.handlerId === userStore.userInfo?.id
  return isAdmin || isHandler
})

/** 抢单：PENDING 状态下显示，成功后刷新详情 */
async function doAccept() {
  try {
    await ElMessageBox.confirm('确认接这张单？', '抢单确认', {
      confirmButtonText: '确认接单',
      cancelButtonText: '再看看',
      type: 'warning'
    })
  } catch {
    return
  }
  accepting.value = true
  try {
    await acceptTicket(ticket.value.id)
    ElMessage.success('抢单成功')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '抢单失败')
    await load()
  } finally {
    accepting.value = false
  }
}

async function doResolve() {
  resolving.value = true
  try {
    await resolveTicket(ticket.value.id, { resolution: resolveForm.resolution })
    ElMessage.success('已标记为已解决')
    resolveVisible.value = false
    await load()
  } finally {
    resolving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    ticket.value = await getTicket(route.params.id)
  } finally {
    loading.value = false
  }
}

async function submitReply() {
  replying.value = true
  try {
    await commentTicket(ticket.value.id, { content: reply.value, internal: internal.value })
    ElMessage.success('已发送')
    reply.value = ''
    await load()
  } finally {
    replying.value = false
  }
}

async function openTransfer() {
  transferVisible.value = true
  if (agents.value.length === 0) {
    try {
      const all = await listAgents()
      agents.value = all.filter((u) => u.role === 'AGENT' && u.id !== ticket.value.handlerId)
    } catch {
      // 403 时保持空列表，用户能看出没有可选坐席
    }
  }
}

async function doTransfer() {
  if (!transferForm.toHandlerId) {
    ElMessage.warning('请选择目标坐席')
    return
  }
  transferring.value = true
  try {
    await transferTicket(ticket.value.id, { ...transferForm })
    ElMessage.success('已转派')
    transferVisible.value = false
    transferForm.toHandlerId = null
    transferForm.reason = ''
    agents.value = []
    await load()
  } finally {
    transferring.value = false
  }
}

function openClose() {
  closeVisible.value = true
}

async function doClose() {
  closing.value = true
  try {
    await closeTicket(ticket.value.id, { resolution: closeForm.resolution })
    ElMessage.success('工单已关闭')
    closeVisible.value = false
    await load()
  } finally {
    closing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.title {
  font-weight: 600;
}

.tags {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.block {
  margin-top: 16px;
}

.block-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.content {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;
  white-space: pre-wrap;
  line-height: 1.8;
}

.actions {
  margin-top: 18px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.hint {
  color: #c0c4cc;
  font-size: 12px;
}

.mt {
  margin-top: 14px;
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

.c-head {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.who {
  color: #606266;
  font-size: 13px;
}

.time {
  color: #c0c4cc;
  font-size: 12px;
}

.c-body {
  white-space: pre-wrap;
}

.reply {
  margin-top: 16px;
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}

.reply-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.log-line {
  display: flex;
  gap: 10px;
  align-items: center;
}

.action {
  font-weight: 600;
}

.log-remark {
  color: #606266;
  font-size: 13px;
  margin-top: 4px;
}

.overdue {
  color: #f56c6c;
}
</style>
