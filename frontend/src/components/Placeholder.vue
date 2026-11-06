<template>
  <div class="page">
    <h2 class="page-title">{{ title }}</h2>
    <p class="page-desc">{{ desc }}</p>

    <div class="placeholder">
      <h3>这一页还没做</h3>
      <p>按任务书安排，本页排在后面的阶段。现在先把结构占住，路由和菜单是通的。</p>
      <ul>
        <li v-for="(item, i) in items" :key="i">{{ item }}</li>
      </ul>
      <p class="plan">计划实现阶段：{{ phase }}</p>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  desc: { type: String, default: '' },
  items: { type: Array, default: () => [] },
  phase: { type: String, default: '待排期' }
})
</script>

<style scoped>
.plan {
  margin-top: 16px;
  color: #c0c4cc;
  font-size: 12px;
}
</style>
