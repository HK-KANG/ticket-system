<template>
  <div class="page" v-loading="loading">
    <h2 class="page-title">数据看板</h2>
    <p class="page-desc">数据来自 /api/ticket/stats 实时聚合。图表的可视化（ECharts）排在 Phase 6，这里先用进度条表达占比。</p>

    <el-row :gutter="14" class="stats">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">工单总数</div>
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-sub">全部状态合计</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">待派单</div>
          <div class="stat-value warn">{{ stats.statusCount?.PENDING || 0 }}</div>
          <div class="stat-sub">还没人接单</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">已超时</div>
          <div class="stat-value danger">{{ stats.overdueCount || 0 }}</div>
          <div class="stat-sub">超过 SLA 且未结束</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">SLA 达成率</div>
          <div class="stat-value ok">{{ stats.slaAchieveRate }}%</div>
          <div class="stat-sub">已关闭工单中按时完成的比例</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>状态分布</template>
          <div v-for="(v, k) in stats.statusCount" :key="k" class="bar-row">
            <span class="bar-label">{{ statusLabel(k) }}</span>
            <el-progress :percentage="pct(v)" :stroke-width="14" :show-text="false" />
            <span class="bar-value">{{ v }}</span>
          </div>
          <el-empty v-if="!stats.statusCount || !Object.keys(stats.statusCount).length" description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header>分类分布</template>
          <div v-for="(v, k) in stats.categoryCount" :key="k" class="bar-row">
            <span class="bar-label">{{ k }}</span>
            <el-progress :percentage="pct(v)" :stroke-width="14" :show-text="false" color="#67c23a" />
            <span class="bar-value">{{ v }}</span>
          </div>
          <el-empty v-if="!stats.categoryCount || !Object.keys(stats.categoryCount).length" description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="mt">
      <template #header>近 7 天新增</template>
      <div class="trend">
        <div v-for="d in stats.dailyTrend" :key="d.date" class="trend-col">
          <div class="trend-bar" :style="{ height: heightOf(d.count) }" :title="`${d.date}: ${d.count}`"></div>
          <div class="trend-count">{{ d.count }}</div>
          <div class="trend-date">{{ d.date.slice(5) }}</div>
        </div>
      </div>
      <el-empty v-if="!stats.dailyTrend || !stats.dailyTrend.length" description="暂无数据" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getStats } from '@/api/ticket'
import { statusLabel } from '@/utils/dict'

const loading = ref(false)
const stats = ref({ total: 0, statusCount: {}, categoryCount: {}, dailyTrend: [], slaAchieveRate: 0, overdueCount: 0 })

async function load() {
  loading.value = true
  try {
    stats.value = await getStats()
  } finally {
    loading.value = false
  }
}

/** 占比：某个状态 / 总数，用于进度条 */
function pct(v) {
  const total = stats.value.total || 0
  if (!total) return 0
  return Math.round((v * 100) / total)
}

/** 趋势柱高度，最高 120px */
function heightOf(count) {
  const max = Math.max(...(stats.value.dailyTrend || []).map((d) => d.count), 1)
  return Math.max(4, Math.round((count / max) * 120)) + 'px'
}

onMounted(load)
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
  color: #1f4e79;
}

.stat-value.warn {
  color: #e6a23c;
}

.stat-value.danger {
  color: #f56c6c;
}

.stat-value.ok {
  color: #67c23a;
}

.stat-sub {
  color: #c0c4cc;
  font-size: 12px;
}

.bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.bar-label {
  width: 70px;
  font-size: 13px;
  color: #606266;
  flex-shrink: 0;
}

.bar-value {
  width: 40px;
  text-align: right;
  font-size: 13px;
  color: #909399;
}

.mt {
  margin-top: 14px;
}

.trend {
  display: flex;
  align-items: flex-end;
  gap: 18px;
  height: 170px;
  padding: 0 8px;
}

.trend-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  flex: 1;
  height: 100%;
}

.trend-bar {
  width: 100%;
  max-width: 44px;
  background: #409eff;
  border-radius: 4px 4px 0 0;
  transition: height 0.3s;
}

.trend-count {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
}

.trend-date {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
