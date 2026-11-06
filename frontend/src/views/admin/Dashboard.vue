<template>
  <div class="page">
    <h2 class="page-title">数据看板</h2>
    <p class="page-desc">工单量趋势、分类分布、SLA 达成率。图表在 Phase 6 接入（需要后端先出统计接口）。</p>

    <el-row :gutter="14" class="stats">
      <el-col :span="6" v-for="s in stats" :key="s.label">
        <el-card shadow="never">
          <div class="stat-label">{{ s.label }}</div>
          <div class="stat-value" :style="{ color: s.color }">{{ s.value }}</div>
          <div class="stat-sub">{{ s.sub }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :span="16">
        <el-card shadow="never" class="chart">
          <div class="chart-title">工单量趋势（近 7 天）</div>
          <div class="chart-empty">图表占位 · Phase 6 用 ECharts 接入</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="chart">
          <div class="chart-title">分类分布</div>
          <div class="chart-empty">图表占位 · Phase 6 用 ECharts 接入</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
// 数字先写死，等后端 /api/admin/stat/* 就绪后换成接口数据
const stats = [
  { label: '今日新增', value: '23', sub: '较昨日 +5', color: '#1f4e79' },
  { label: '待派单', value: '8', sub: '其中 P0 1 单', color: '#e6a23c' },
  { label: '处理中', value: '11', sub: '平均处理 2.4 小时', color: '#409eff' },
  { label: 'SLA 达成率', value: '96.2%', sub: '低于 95% 会标红', color: '#67c23a' }
]
</script>

<style scoped>
.stats {
  margin-bottom: 14px;
}

.stat-label {
  color: #909399;
  font-size: 13px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  margin: 6px 0;
}

.stat-sub {
  color: #c0c4cc;
  font-size: 12px;
}

.chart {
  height: 320px;
}

.chart-title {
  font-weight: 600;
  margin-bottom: 12px;
}

.chart-empty {
  height: 240px;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}
</style>
