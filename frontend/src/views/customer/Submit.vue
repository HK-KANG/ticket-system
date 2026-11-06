<template>
  <div class="page">
    <h2 class="page-title">提交工单</h2>
    <p class="page-desc">描述清楚问题，系统会按分类和优先级自动算出响应时限。</p>

    <el-card shadow="never" style="max-width: 860px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="一句话说清问题，如：申请退款，商品未收到" />
        </el-form-item>

        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 240px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>

        <el-form-item label="优先级" prop="priority">
          <el-radio-group v-model="form.priority">
            <el-radio-button v-for="p in priorities" :key="p" :value="p">{{ p }}</el-radio-button>
          </el-radio-group>
          <div class="hint">P0 紧急 · P1 高 · P2 中 · P3 低，选错会影响 SLA 时限</div>
        </el-form-item>

        <el-form-item label="问题描述" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" maxlength="5000" show-word-limit
                    placeholder="订单号、发生时间、你的诉求，写得越清楚处理越快" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="onSubmit">提交工单</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createTicket } from '@/api/ticket'
import { CATEGORY_OPTIONS, PRIORITY_OPTIONS } from '@/utils/dict'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const categories = CATEGORY_OPTIONS
const priorities = PRIORITY_OPTIONS

const form = reactive({ title: '', category: '', priority: 'P2', content: '' })

/**
 * 幂等键。同一份表单反复提交（网络超时后重试、用户连点两次）必须用同一个 requestId，
 * 否则后端会各建一张单。所以它在页面打开时生成一次，提交成功后才换新的。
 */
let requestId = newUuid()

function newUuid() {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID()
  return 'req-' + Date.now() + '-' + Math.random().toString(16).slice(2, 10)
}

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  content: [
    { required: true, message: '请描述问题', trigger: 'blur' },
    { min: 10, message: '至少写 10 个字，方便坐席判断', trigger: 'blur' }
  ]
}

async function onSubmit() {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  submitting.value = true
  try {
    const ticket = await createTicket({
      title: form.title,
      category: form.category,
      priority: form.priority,
      content: form.content,
      requestId
    })
    ElMessage.success(`提交成功，单号 ${ticket.ticketNo}`)
    requestId = newUuid()
    router.push('/customer/tickets')
  } catch (e) {
    // 失败时保留 requestId：用户改完再点提交，仍算同一次请求，不会重复建单
    ElMessage.error(e.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

function reset() {
  formRef.value.resetFields()
  requestId = newUuid()
}
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: #909399;
  margin-left: 12px;
}
</style>
