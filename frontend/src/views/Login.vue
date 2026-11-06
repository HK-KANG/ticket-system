<template>
  <div class="login-page">
    <div class="login-box">
      <div class="brand">
        <el-icon :size="28" color="#1f4e79"><Service /></el-icon>
        <h1>售后工单系统</h1>
        <p class="sub">客户提工单 · 坐席抢单处理 · 管理员看数据</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="onSubmit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" size="large" />
        </el-form-item>
        <el-button type="primary" size="large" class="submit" :loading="loading" @click="onSubmit">
          登录
        </el-button>
      </el-form>

      <el-divider>演示账号（一键填充）</el-divider>
      <div class="demo">
        <el-button v-for="u in demoUsers" :key="u.username" plain size="small" @click="fill(u)">
          {{ u.realName }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { MOCK_USERS } from '@/api/mock'
import { homePathOf } from '@/utils/role'

const router = useRouter()
const route = useRoute()
const user = useUserStore()

const formRef = ref()
const loading = ref(false)
// 演示账号始终显示：Phase 3 起所有数据都是真的，这几个账号也确实存在（V2 初始化脚本）
const demoUsers = MOCK_USERS

const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function fill(u) {
  form.username = u.username
  form.password = u.password
}

async function onSubmit() {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  loading.value = true
  try {
    await user.login({ username: form.username, password: form.password })
    ElMessage.success(`欢迎，${user.realName}`)
    const target = route.query.redirect || homePathOf(user.role)
    router.push(target)
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef3f9 0%, #dfe9f5 100%);
}

.login-box {
  width: 420px;
  background: #fff;
  border-radius: 12px;
  padding: 36px 40px 28px;
  box-shadow: 0 8px 32px rgba(31, 78, 121, 0.12);
}

.brand {
  text-align: center;
  margin-bottom: 24px;
}

.brand h1 {
  font-size: 22px;
  margin: 8px 0 4px;
  color: #1f4e79;
}

.sub {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.submit {
  width: 100%;
  margin-top: 4px;
}

.demo {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
}
</style>
