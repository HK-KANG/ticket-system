<template>
  <div class="page">
    <h2 class="page-title">提交工单</h2>
    <p class="page-desc">描述清楚问题，系统会按分类和优先级自动算出响应时限。</p>

    <el-card shadow="never" style="max-width: 860px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="一句话说清问题，如：申请退款，商品未收到" />
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
          <el-input v-model="form.content" type="textarea" :rows="6" maxlength="1000" show-word-limit
                    placeholder="订单号、发生时间、你的诉求，写得越清楚处理越快" />
        </el-form-item>

        <el-form-item label="附件">
          <el-upload drag action="#" :auto-upload="false" :limit="3" :show-file-list="true">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到这里，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">最多 3 个文件，单个不超过 10MB（后端上传接口在 Phase 3 接）</div>
            </template>
          </el-upload>
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
import { UploadFilled } from '@element-plus/icons-vue'
import { CATEGORY_OPTIONS, PRIORITY_OPTIONS } from '@/api/mock'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const categories = CATEGORY_OPTIONS
const priorities = PRIORITY_OPTIONS

const form = reactive({
  title: '',
  category: '',
  priority: 'P2',
  content: ''
})

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
    // 后端 POST /api/ticket 在 Phase 3 实现；这里先本地成功，保证流程能走通
    await new Promise((r) => setTimeout(r, 500))
    ElMessage.success('提交成功，可在「我的工单」查看进度')
    router.push('/customer/tickets')
  } finally {
    submitting.value = false
  }
}

function reset() {
  formRef.value.resetFields()
}
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: #909399;
  margin-left: 12px;
}
</style>
